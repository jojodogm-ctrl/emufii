// SDK delivery pacing only; no game memory, instructions or synthetic receipts.
#pragma once
#include <cstring>
namespace melonDS::wm_sdk {
template<class Get> bool experimentalCartridgePacing(Get get) {
    auto one=[&](const char* key){const char* value=get(key);return value && !std::strcmp(value,"1");};
    return one("MPDS_EXPERIMENTAL_ROOM") && one("MPDS_WMSDK_EXPERIMENTAL") && !one("MPDS_DOWNLOAD_FREE");
}
template<class Get> bool freeDownloadPacing(Get get) {
    auto one=[&](const char* key){const char* value=get(key);return value && !std::strcmp(value,"1");};
    return one("MPDS_EXPERIMENTAL_ROOM") && one("MPDS_WMSDK_EXPERIMENTAL") && one("MPDS_DOWNLOAD_FREE");
}
}
