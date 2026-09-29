/**
 * ADPIA API Client & Normalization Layer (Isolated Step 1)
 * Handles authentication, fetching conversions, aff_sub parsing, and normalization.
 */

const axios = require('axios');

class AdpiaError extends Error {
  constructor(message, code, details = null) {
    super(message);
    this.name = 'AdpiaError';
    this.code = code; // ADPIA_CONFIG_ERROR, ADPIA_AUTH_ERROR, ADPIA_HTTP_ERROR, ADPIA_NETWORK_ERROR, ADPIA_INVALID_RESPONSE, ADPIA_INVALID_PARAMETERS
    this.details = details;
  }
}

/**
 * Parses ADPIA aff_sub parameter string.
 * Example: "&u_id=casi_test_001&utm_source=zalo&utm_medium=zalo&utm_campaign=zalo"
 * or "casi_test_001"
 * or "u_id=casi_test_001&utm_source=zalo"
 */
function parseAdpiaAffSub(affSub) {
  if (affSub === null || affSub === undefined || String(affSub).trim() === '') {
    return {
      raw: affSub !== null && affSub !== undefined ? String(affSub) : null,
      uId: null,
      params: {}
    };
  }

  const raw = String(affSub).trim();
  const params = {};
  let uId = null;

  // If it doesn't contain '=' or '&', treat the raw string as the direct uId (e.g. "casi_test_001")
  if (!raw.includes('=') && !raw.includes('&')) {
    uId = decodeURIComponent(raw);
    params['u_id'] = uId;
    return { raw, uId, params };
  }

  // Clean leading ampersand if present
  const cleanStr = raw.startsWith('&') ? raw.slice(1) : raw;
  const pairs = cleanStr.split('&');

  for (const pair of pairs) {
    if (!pair) continue;
    const eqIndex = pair.indexOf('=');
    if (eqIndex === -1) {
      const decodedKey = decodeURIComponent(pair.trim());
      params[decodedKey] = '';
      if (decodedKey === 'u_id' || decodedKey === 'uid' || decodedKey === 'userid') {
        uId = '';
      }
    } else {
      const key = decodeURIComponent(pair.slice(0, eqIndex).trim());
      const val = decodeURIComponent(pair.slice(eqIndex + 1).trim());
      params[key] = val;
      if (key === 'u_id' || key === 'uid' || key === 'userid') {
        uId = val;
      }
    }
  }

  return {
    raw,
    uId: uId !== null && uId !== undefined && uId !== '' ? uId : null,
    params
  };
}

/**
 * Normalizes ADPIA status to Casi-neutral status.
 * pending -> pending
 * approve, confirm -> approved
 * reject, cancel -> cancelled
 */
function normalizeAdpiaStatus(status) {
  if (!status) return 'pending';
  const s = String(status).toLowerCase().trim();
  if (s === 'approve' || s === 'approved' || s === 'confirm' || s === 'confirmed') {
    return 'approved';
  }
  if (s === 'reject' || s === 'rejected' || s === 'cancel' || s === 'cancelled') {
    return 'cancelled';
  }
  return 'pending';
}

/**
 * Normalizes raw ADPIA conversion object into a stable provider-neutral structure.
 */
function normalizeAdpiaConversion(raw) {
  if (!raw || typeof raw !== 'object') {
    throw new AdpiaError('Invalid raw conversion object provided for normalization', 'ADPIA_INVALID_PARAMETERS');
  }

  const affSubRaw = raw.aff_sub !== undefined && raw.aff_sub !== null ? String(raw.aff_sub) : '';
  const parsedAffSub = parseAdpiaAffSub(affSubRaw);

  return {
    provider: 'adpia',
    conversionId: raw.conversion_id ? String(raw.conversion_id).trim() : null,
    orderId: raw.ocd ? String(raw.ocd).trim() : null,
    productId: raw.pcd ? String(raw.pcd).trim() : null,
    productName: raw.pname ? String(raw.pname).trim() : null,
    categoryCode: raw.ccd ? String(raw.ccd).trim() : null,
    categoryName: raw.ccd_name ? String(raw.ccd_name).trim() : null,
    categoryCode2: raw.ccd2 ? String(raw.ccd2).trim() : null,
    categoryName2: raw.ccd2_name ? String(raw.ccd2_name).trim() : null,
    device: raw.device ? String(raw.device).trim() : null,
    salesAmount: raw.sales !== undefined && raw.sales !== null ? Number(raw.sales) : 0,
    actualAmount: raw.actual_amount !== undefined && raw.actual_amount !== null ? Number(raw.actual_amount) : 0,
    commission: raw.commission !== undefined && raw.commission !== null ? Number(raw.commission) : 0,
    quantity: raw.cnt !== undefined && raw.cnt !== null ? Number(raw.cnt) : 1,
    offerId: raw.offer_id ? String(raw.offer_id).trim() : null,
    affiliateId: raw.aid ? String(raw.aid).trim() : null,
    status: normalizeAdpiaStatus(raw.status),
    rawStatus: raw.status || null,
    providerSubId: parsedAffSub.uId,
    affSubRaw: affSubRaw,
    trackingParams: parsedAffSub.params,
    ip: raw.ip ? String(raw.ip).trim() : null,
    conversionDate: raw.ymd ? String(raw.ymd).trim() : null,
    conversionTime: raw.his ? String(raw.his).trim() : null,
    updatedAt: raw.update_at ? String(raw.update_at).trim() : null,
    completeTime: raw.complete_time !== undefined ? raw.complete_time : null,
    shopId: raw.shop_id !== undefined && raw.shop_id !== null ? String(raw.shop_id).trim() : null,
    shopName: raw.shop_name ? String(raw.shop_name).trim() : null,
    type: raw.type ? String(raw.type).trim() : null
  };
}

/**
 * ADPIA API Client class
 */
class AdpiaClient {
  constructor(config = {}) {
    this.baseUrl = config.baseUrl || process.env.ADPIA_API_BASE_URL || 'https://newapi.adpia.vn';
    this.username = config.username || process.env.ADPIA_API_USERNAME;
    this.password = config.password || process.env.ADPIA_API_PASSWORD;
    this.timeout = config.timeout || 15000;
  }

  /**
   * Fetches conversions from ADPIA API.
   * Options: { sdate, edate, page, limit, mid, status, ocd, pcd, group }
   */
  async getConversions(options = {}) {
    if (!this.username || !this.password) {
      throw new AdpiaError(
        'ADPIA API credentials (ADPIA_API_USERNAME / ADPIA_API_PASSWORD) are not configured.',
        'ADPIA_CONFIG_ERROR'
      );
    }

    const { sdate, edate, page, limit, mid, status, ocd, pcd, group } = options;

    if (!sdate || !edate) {
      throw new AdpiaError('sdate and edate (YYYYMMDD) are required parameters for ADPIA conversions.', 'ADPIA_INVALID_PARAMETERS');
    }

    const params = {
      sdate: String(sdate).trim(),
      edate: String(edate).trim()
    };

    if (page !== undefined && page !== null) params.page = Number(page);
    if (limit !== undefined && limit !== null) params.limit = Number(limit);
    if (mid) params.mid = String(mid).trim();
    if (status) params.status = String(status).trim();
    if (ocd) params.ocd = String(ocd).trim();
    if (pcd) params.pcd = String(pcd).trim();
    if (group) params.group = String(group).trim();

    const authHeader = 'Basic ' + Buffer.from(`${this.username}:${this.password}`).toString('base64');

    let response;
    try {
      response = await axios.get(`${this.baseUrl}/v2/affiliate/get_conversions`, {
        params,
        headers: {
          'Authorization': authHeader,
          'Content-Type': 'application/json'
        },
        timeout: this.timeout
      });
    } catch (error) {
      if (error.response) {
        const status = error.response.status;
        if (status === 401 || status === 403) {
          throw new AdpiaError(`ADPIA Authentication failed (HTTP ${status})`, 'ADPIA_AUTH_ERROR', { status });
        }
        throw new AdpiaError(`ADPIA API HTTP error (HTTP ${status})`, 'ADPIA_HTTP_ERROR', { status, data: error.response.data });
      } else if (error.request) {
        throw new AdpiaError(`ADPIA network error / timeout: ${error.message}`, 'ADPIA_NETWORK_ERROR', { message: error.message });
      } else {
        throw new AdpiaError(`ADPIA request setup error: ${error.message}`, 'ADPIA_CONFIG_ERROR', { message: error.message });
      }
    }

    const resData = response.data;
    if (!resData || typeof resData !== 'object') {
      throw new AdpiaError('ADPIA returned invalid non-JSON response structure', 'ADPIA_INVALID_RESPONSE');
    }

    if (resData.code !== undefined && resData.code !== null && Number(resData.code) !== 200) {
      throw new AdpiaError(`ADPIA API returned error code ${resData.code}: ${resData.message || 'Unknown error'}`, 'ADPIA_INVALID_RESPONSE', { code: resData.code, message: resData.message });
    }

    const dataContainer = resData.data;
    if (!dataContainer || typeof dataContainer !== 'object' || !Array.isArray(dataContainer.data)) {
      throw new AdpiaError('ADPIA response missing expected data.data array', 'ADPIA_INVALID_RESPONSE');
    }

    const normalizedItems = dataContainer.data.map(item => normalizeAdpiaConversion(item));

    return {
      success: true,
      message: resData.message || 'OK',
      code: resData.code || 200,
      metadata: {
        sdate: dataContainer.sdate || sdate,
        edate: dataContainer.edate || edate,
        count: dataContainer.count !== undefined ? dataContainer.count : normalizedItems.length,
        limit: dataContainer.limit || limit || null,
        page: dataContainer.page || page || null
      },
      rawItems: dataContainer.data,
      conversions: normalizedItems
    };
  }
}

module.exports = {
  AdpiaError,
  AdpiaClient,
  parseAdpiaAffSub,
  normalizeAdpiaStatus,
  normalizeAdpiaConversion
};
