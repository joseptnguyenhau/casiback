/**
 * ADPIA Persistence Layer (Step 2)
 * Safely persists normalized ADPIA conversions into Firestore idempotently
 * without any financial balance mutations or ledger entries.
 */

const { AdpiaClient, normalizeAdpiaConversion, parseAdpiaAffSub } = require('./adpiaClient');

/**
 * Syncs ADPIA conversions from API into Firestore idempotently.
 * @param {Object} options - { sdate, edate, page, limit, mid, status, ocd, pcd, group, client }
 * @param {Object} db - Firestore database instance
 * @returns {Promise<Object>} Summary { fetched, persisted, created, updated, unresolvedAttribution, failed }
 */
async function syncAdpiaConversions(options = {}, db) {
  if (!db) {
    throw new Error('Firestore db instance is required for ADPIA synchronization.');
  }

  const client = options.client || new AdpiaClient({
    username: options.username,
    password: options.password,
    baseUrl: options.baseUrl
  });

  const summary = {
    fetched: 0,
    persisted: 0,
    created: 0,
    updated: 0,
    unresolvedAttribution: 0,
    failed: 0,
    errors: []
  };

  let apiResult;
  try {
    apiResult = await client.getConversions(options);
  } catch (err) {
    throw new Error(`Failed to fetch ADPIA conversions: ${err.message}`);
  }

  const conversions = apiResult.conversions || [];
  summary.fetched = conversions.length;

  const batchSize = 500;
  let batch = db.batch();
  let batchCount = 0;

  for (const conv of conversions) {
    try {
      if (!conv.conversionId) {
        summary.failed++;
        summary.errors.push({ conversionId: null, error: 'Missing conversionId' });
        continue;
      }

      const docId = `adpia_${conv.conversionId}`;
      const docRef = db.collection('transactions').doc(docId);

      // User attribution from aff_sub uId
      const userId = conv.providerSubId ? String(conv.providerSubId).trim() : null;
      const attributionStatus = userId ? 'resolved' : 'unresolved';

      if (!userId) {
        summary.unresolvedAttribution++;
      }

      // Prepare clean raw provider data (strip any accidental secrets/headers if present)
      const rawPayload = { ...conv.trackingParams, aff_sub: conv.affSubRaw };
      // Ensure no credentials or auth headers stored
      delete rawPayload.password;
      delete rawPayload.authorization;
      delete rawPayload.auth;

      const now = new Date();

      await db.runTransaction(async (transaction) => {
        const docSnap = await transaction.get(docRef);

        let isNew = false;
        let createdAt = now;
        let firstSeenAt = now;

        if (docSnap.exists) {
          const existingData = docSnap.data();
          // Identity conflict check
          if (existingData.provider && existingData.provider !== 'adpia') {
            throw new Error(`Provider identity conflict for ${docId}: existing provider is ${existingData.provider}`);
          }
          if (existingData.providerConversionId && existingData.providerConversionId !== conv.conversionId) {
            throw new Error(`Provider conversion ID conflict for ${docId}`);
          }
          createdAt = existingData.createdAt || now;
          firstSeenAt = existingData.firstSeenAt || now;
          summary.updated++;
        } else {
          isNew = true;
          summary.created++;
        }

        const transactionData = {
          provider: 'adpia',
          providerConversionId: conv.conversionId,
          conversionId: conv.conversionId,
          userId: userId,
          attributionStatus: attributionStatus,
          orderId: conv.orderId,
          productId: conv.productId,
          productName: conv.productName,
          categoryCode: conv.categoryCode,
          categoryName: conv.categoryName,
          device: conv.device,
          salesAmount: conv.salesAmount,
          actualAmount: conv.actualAmount,
          commission: conv.commission,
          quantity: conv.quantity,
          offerId: conv.offerId,
          affiliateId: conv.affiliateId,
          providerStatus: conv.rawStatus,
          normalizedStatus: conv.status,
          providerSubId: conv.providerSubId,
          affSubRaw: conv.affSubRaw,
          trackingParams: conv.trackingParams || {},
          shopId: conv.shopId,
          shopName: conv.shopName,
          currency: 'VND',
          conversionDate: conv.conversionDate,
          conversionTime: conv.conversionTime,
          updatedAt: now,
          lastSeenAt: now,
          createdAt: createdAt,
          firstSeenAt: firstSeenAt,
          rawProviderData: rawPayload
        };

        transaction.set(docRef, transactionData, { merge: true });
      });

      summary.persisted++;
    } catch (err) {
      summary.failed++;
      summary.errors.push({ conversionId: conv.conversionId, error: err.message });
      console.error(`Failed to persist ADPIA conversion ${conv.conversionId}:`, err.message);
    }
  }

  return summary;
}

module.exports = {
  syncAdpiaConversions
};
