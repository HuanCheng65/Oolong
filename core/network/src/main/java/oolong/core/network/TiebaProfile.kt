package oolong.core.network

/** How the response body is encoded. */
enum class TiebaProtocol { PROTOBUF, FORM }

/**
 * The two network stacks requests go through, which send different headers, cookies and
 * common parameters.
 */
enum class TiebaStack {
    /** `NetWork`: sign-in, likes, follows, login, sync and uploads. */
    CLASSIC,

    /** `TbHttpMessageTask`: protobuf requests, posting and some form requests. */
    MESSAGE_TASK,

    /** Requests the in-app web pages make, such as the search page. */
    WEB_VIEW,
}

/**
 * The client identities requests are sent as: one client version, through each stack. Version
 * numbers live only here.
 */
enum class TiebaProfile(
    val protocol: TiebaProtocol,
    val stack: TiebaStack,
    val baseUrl: String = "https://tiebac.baidu.com/",
) {
    PROTO(protocol = TiebaProtocol.PROTOBUF, stack = TiebaStack.MESSAGE_TASK),

    /** Form requests of the classic stack: sign-in, likes, follows, login, sync. */
    FORM(protocol = TiebaProtocol.FORM, stack = TiebaStack.CLASSIC),

    /** Form requests of the message task stack, such as the message reminder. */
    MESSAGE_FORM(protocol = TiebaProtocol.FORM, stack = TiebaStack.MESSAGE_TASK),

    /** Data requests of the in-app web pages, answered in JSON. */
    WEB(protocol = TiebaProtocol.FORM, stack = TiebaStack.WEB_VIEW, baseUrl = "https://tieba.baidu.com/"),
    ;

    val version: String get() = VERSION
    val frameworkVersion: String get() = FRAMEWORK_VERSION
    val sdkVersion: String get() = SDK_VERSION
    val swanGameVersion: String get() = SWAN_GAME_VERSION

    /** The version of the bundled thread page, which protobuf requests name. */
    val packageVersion: String get() = PACKAGE_VERSION

    private companion object {
        const val VERSION = "22.10.1.0"
        const val FRAMEWORK_VERSION = "4220001"
        const val SDK_VERSION = "3.36.0"
        const val SWAN_GAME_VERSION = "2035000"
        const val PACKAGE_VERSION = "hybrid-main-pb_1.0.386.1"
    }
}
