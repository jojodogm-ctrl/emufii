#pragma once
#include <optional>
namespace melonDS::wm_sdk::sharing_policy {
inline constexpr unsigned Capacity=64,LegacyVersion=3,PipelineVersion=4,BlockVersion=5;
inline constexpr bool validLead(unsigned lead){return lead>=2&&lead<=Capacity;}
inline constexpr bool validClaim(unsigned version,unsigned depth){
    return (version==LegacyVersion&&depth==Capacity)||((version==PipelineVersion||version==BlockVersion)&&validLead(depth));
}
// Canonical cold configuration, never atoi prefix acceptance or live mutation.
inline std::optional<unsigned> parseLead(const char* text){
    if(!text||!*text)return std::nullopt;
    unsigned n=0;for(unsigned i=0;text[i];++i){
        if(i>=2||text[i]<'0'||text[i]>'9'||(!i&&text[i]=='0'))return std::nullopt;
        n=n*10+unsigned(text[i]-'0');
    }
    return validLead(n)?std::optional<unsigned>(n):std::nullopt;
}
}
