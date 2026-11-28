package oolong.core.network.forum

import kotlinx.serialization.json.JsonPrimitive
import oolong.core.network.identity.DeviceIdentity
import oolong.core.network.identity.NetworkType
import oolong.core.network.protos.AppTransmitData

/**
 * What the forum page's requests say of adverts, as the official client says it with none seen:
 * the ad network's own state (which apps are installed, bids, adverts cached) left empty, its
 * fixed values as they are. Nothing here asks for adverts or reads them.
 */
internal object FrsAds {
    private const val NadCoreVersion = "6.54.5"

    /** The advert slot the feed would use: one on Wi-Fi, where videos play by themselves, another on mobile data. */
    fun bearSid(networkType: Int): String = if (networkType == NetworkType.WIFI) "6051009916-1474920995" else "6051009917-328151198"

    /**
     * frs/page's `ad_ext_params`, its keys in the order the official client writes them.
     *
     * @param preThreads threads the list already shows, when loading more
     * @param requestType 0 for a first page or a refresh, 1 for loading more
     */
    fun extParams(preThreads: Int, requestType: Int): String =
        """{"pre_ad_thread_count":$preThreads,"iadex":"","req_type":$requestType,"floor_info":"","nad_core_version":"$NadCoreVersion"}"""

    /** frs/threadlist's: the count and the kind of request only once the list shows threads. */
    fun threadListExtParams(preThreads: Int): String = if (preThreads > 0) {
        extParams(preThreads, requestType = 1)
    } else {
        """{"iadex":"","floor_info":"","nad_core_version":"$NadCoreVersion"}"""
    }

    /** generalTabList's, which names the forum and the advert context too. */
    fun generalTabExtParams(forumName: String, preThreads: Int, requestType: Int): String =
        """{"forum_name":${JsonPrimitive(forumName)},"pre_ad_thread_count":$preThreads,"iadex":"","req_type":$requestType,""" +
            """"floor_info":"","nad_core_version":"$NadCoreVersion","ad_context_list":""}"""

    /** `app_transmit_data` as the forum page sends it: the SIM's operator and the time zone. */
    fun transmitData(device: DeviceIdentity) = AppTransmitData(
        carrier = device.carrier,
        ismi = "",
        local_tz_name = device.timeZone,
        yy_version = "",
    )
}

/**
 * Where the forum page says it was opened from: the forums list, the entry the app's own lists
 * stand for, and no link's details.
 */
internal object FrsEntry {
    const val CallFrom = 3
    const val StType = "recom_flist"

    /** `{}`, percent-encoded. */
    const val LogParam = "%7B%7D"
}
