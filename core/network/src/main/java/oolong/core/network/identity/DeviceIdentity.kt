package oolong.core.network.identity

/**
 * Identifiers and properties of this device, as sent in common parameters.
 *
 * Values must stay the same across requests and app updates: when they change, the server sees
 * the logged-in account move to a new device, which can trigger verification.
 */
interface DeviceIdentity {
    /** `Settings.Secure.ANDROID_ID` as is (16 hex digits), empty when unavailable. */
    val androidId: String

    /** `cuid`, also sent as `cuid_galaxy2`: 32 hex digits, `|V`, 8 base32 characters. */
    val cuid: String

    /** `c3_aid`: `A00-`, 32 base32 characters, `-`, 8 base32 characters. */
    val c3Aid: String

    /** IMEI, or 15 zeros when it can't be read (always the case since Android 10). */
    val imei: String

    /** The OAID in its `A10-` form (base32 of it, then a check), empty when unsupported. */
    val oaid: String

    val model: String
    val brand: String

    /** `Build.VERSION.RELEASE`, such as `14`. */
    val osVersion: String

    val screenWidth: Int
    val screenHeight: Int
    val screenDensity: Float

    /** Current connection as a [NetworkType] code. */
    val networkType: Int

    /** Performance score in `0..1`. */
    val deviceScore: Float

    /** The system WebView user agent, which prefixes the client's own. */
    val webViewUserAgent: String
}
