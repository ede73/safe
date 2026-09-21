package fi.iki.ede.backup

import kotlin.time.ExperimentalTime


@ExperimentalTime
abstract class ExportConfig(currentCodedVersion: ExportVersion) {
    // IMPORTANT: If you ever introduce a breaking change, make sure to advance the version code
    val currentVersion = currentCodedVersion

    enum class ExportVersion(val version: String) {
        V1("1")
    }

    companion object {
        const val ATTRIBUTE_PREFIX_IV = "iv_"
        const val ATTRIBUTE_PREFIX_CIPHER = "cipher_"

        enum class Elements(val value: String) {
            ROOT_PASSWORD_SAFE("PasswordSafe"),
            CATEGORY("category"),
            SITE_ENTRY("item"),
            SITE_ENTRY_DESCRIPTION("description"),
            SITE_ENTRY_WEBSITE("website"),
            SITE_ENTRY_USERNAME("username"),
            SITE_ENTRY_PASSWORD("password"),
            SITE_ENTRY_NOTE("note"),
            SITE_ENTRY_PHOTO("photo"),
            SITE_ENTRY_EXTENSION("extension"),
            IMPORTS("imports"),
            IMPORTS_GPM("gpm"),
            IMPORTS_GPM_ITEM("gpmitem"),
            IMPORTS_CXF("cxf"),
            IMPORTS_CXF_ACCOUNT("cxfaccount"),
            IMPORTS_CXF_IMPORT("cxfimport"),
            IMPORTS_CXF_PASSKEY("cxfpasskey"),
        }

        enum class Attributes(val value: String) {
            ROOT_PASSWORD_SAFE_VERSION("version"),
            ROOT_PASSWORD_SAFE_CREATION_TIME("created"),
            CATEGORY_NAME("name"),
            SITE_ENTRY_ID("ID"),
            SITE_ENTRY_PASSWORD_CHANGED("changed"),
            SITE_ENTRY_DELETED("deleted"),
            IV("iv"),
            IMPORTS_GPM_ITEM_ID("name"),
            IMPORTS_GPM_ITEM_MAP_TO_SITE_ENTRY("password_ids"),
            IMPORTS_GPM_ITEM_NAME("name"),
            IMPORTS_GPM_ITEM_URL("url"),
            IMPORTS_GPM_ITEM_USERNAME("username"),
            IMPORTS_GPM_ITEM_PASSWORD("password"),
            IMPORTS_GPM_ITEM_NOTE("note"),
            IMPORTS_GPM_ITEM_HASH("hash"),
            IMPORTS_GPM_ITEM_STATUS("status"),
            CXF_ACCOUNT_ID("cxf_account_id"),
            CXF_ACCOUNT_EMAIL("email"),
            CXF_ITEM_ID("cxf_item_id"),
            CXF_ITEM_TYPE("type"),
            CXF_ITEM_NAME("name"),
            CXF_ITEM_URL("url"),
            CXF_ITEM_USERNAME("username"),
            CXF_ITEM_PASSWORD("password"),
            CXF_ITEM_NOTE("note"),
            CXF_ITEM_HASH("hash"),
            CXF_ITEM_FLAGGED_IGNORED("flagged_ignored"),
            CXF_PASSKEY_RELYING_PARTY("relying_party"),
            CXF_PASSKEY_USER_NAME("user_name"),
            CXF_PASSKEY_USER_DISPLAY_NAME("user_display_name"),
            CXF_PASSKEY_USER_HANDLE("user_handle"),
            CXF_PASSKEY_CREDENTIAL_ID("credential_id"),
        }
    }
}
