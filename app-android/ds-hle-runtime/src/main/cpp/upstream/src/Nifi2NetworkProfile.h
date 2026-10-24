#pragma once
#include <array>
#include <cstdint>
#include <cstdlib>
#include <fstream>
#include <optional>
#include <sstream>
#include <stdexcept>
#include <string>

// Explicit, opt-in trusted-lab routing, NOT authentication or Internet security.
// Default callers remain loopback-only. Never infer trust from a private IP:
// the file's acknowledgement is required, and public/wildcard addresses fail.
namespace nifi2::network_profile {
struct Endpoint { std::uint32_t ip=0; unsigned port=0; };
inline unsigned decimal(const std::string& s,unsigned maximum){
    if(s.empty() || (s.size()>1 && s[0]=='0'))throw std::runtime_error("network profile decimal");
    unsigned n=0;
    for(char c:s){if(c<'0'||c>'9'||n>maximum/10 || n*10+unsigned(c-'0')>maximum)
        throw std::runtime_error("network profile number range");n=n*10+unsigned(c-'0');}
    return n;
}
inline std::uint32_t address(const std::string& text,bool allowExactHost=false){
    // V2 permits an explicitly configured /32 host (e.g. a VPN's .0 address).
    // This is still ONE bind/connect address, never a subnet or routing rule.
    const bool exact=allowExactHost && text.size()>3 && text.compare(text.size()-3,3,"/32")==0;
    std::istringstream stream(exact?text.substr(0,text.size()-3):text);std::string part;std::uint32_t value=0;
    for(unsigned i=0;i<4;++i){if(!std::getline(stream,part,'.'))throw std::runtime_error("network profile IPv4");
        value=(value<<8)|decimal(part,255);}
    if(stream.good() || !stream.eof())throw std::runtime_error("network profile IPv4 suffix");
    const auto first=value>>24;
    const bool allowed=first==10 || (value>>20)==0xac1 || (value>>16)==0xc0a8 ||
        (value>>22)==0x191 || (value>>8)==0x7f0000;
    // V1 retains its conservative suffix check. V2 /32 explicitly names a
    // host even if its last octet is 0/255. Public, unspecified, link-local,
    // DNS, IPv6, wildcard and subnet listeners are rejected in both versions.
    if(!allowed || ((! (value&255) || (value&255)==255) && !exact))throw std::runtime_error("network profile requires a private host address");
    return value;
}
struct Profile {
    Endpoint broker;unsigned count=0;std::array<Endpoint,8> peers{};
    static Profile parse(const std::string& text){
        if(text.size()>2048)throw std::runtime_error("network profile too large");
        std::istringstream in(text);std::string token,ip,port,n;
        auto expect=[&](const char* name){if(!(in>>token)||token!=name)throw std::runtime_error("network profile syntax");};
        if(!(in>>token)||(token!="NIFI2-LAB-V1"&&token!="NIFI2-LAB-V2"))throw std::runtime_error("network profile version");
        const bool exactHosts=token=="NIFI2-LAB-V2";
        auto endpoint=[&](){if(!(in>>ip>>port))throw std::runtime_error("network profile endpoint");
            Endpoint e{address(ip,exactHosts),decimal(port,65535)};if(e.port<1024)throw std::runtime_error("network profile port");return e;};
        expect("TRUSTED_PRIVATE_NETWORK");expect("broker");
        Profile p;p.broker=endpoint();expect("players");if(!(in>>n))throw std::runtime_error("network profile players");
        p.count=decimal(n,8);if(p.count<2)throw std::runtime_error("network profile player count");
        for(unsigned i=0;i<p.count;++i){expect("peer");if(!(in>>n)||decimal(n,7)!=i)throw std::runtime_error("network profile peer order");
            p.peers[i]=endpoint();for(unsigned j=0;j<i;++j)if(p.peers[i].ip==p.peers[j].ip&&p.peers[i].port==p.peers[j].port)
                throw std::runtime_error("network profile duplicate endpoint");}
        if(in>>token)throw std::runtime_error("network profile trailing content");
        return p;
    }
    bool allows(std::uint32_t ip) const {for(unsigned i=0;i<count;++i)if(peers[i].ip==ip)return true;return false;}
};
inline std::optional<Profile> environment(){
    const char* path=std::getenv("MPDS_NIFI2_NETWORK_FILE");if(!path)return std::nullopt;
    if(!*path)throw std::runtime_error("empty network profile path");
    std::ifstream file(path,std::ios::binary);if(!file)throw std::runtime_error("network profile unreadable");
    std::string text(2049,'\0');file.read(text.data(),text.size());text.resize(static_cast<std::size_t>(file.gcount()));
    if(file.bad())throw std::runtime_error("network profile read error");return Profile::parse(text);
}
inline unsigned local_owner(unsigned count){
    const char* id=std::getenv("MPDS_NIFI2_ID");if(!id)throw std::runtime_error("network profile requires player ID");
    const auto n=decimal(id,count);if(!n)throw std::runtime_error("network profile player ID is one-based");return n-1;
}
}
