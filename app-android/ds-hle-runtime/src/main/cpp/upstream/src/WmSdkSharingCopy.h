#pragma once
// Semantics of the fully fingerprinted SDK MI_CpuClear16/MI_CpuCopy16 loops.
// Admission, CPU permissions, bus effects and cycle charging belong to caller.
#include <cstdint>
#include <stdexcept>
namespace melonDS::wm_sdk::sharing {
enum class MemoryOp {Fill16,Copy16};
struct MemoryResult {std::uint32_t index,last;};
template<class Read16,class Write16>
MemoryResult memory16(MemoryOp op,std::uint32_t sourceOrValue,std::uint32_t destination,
                      unsigned length,Read16 read,Write16 write){
    if(!length||(length&1)||length>1024||(destination&1)||
        destination>0xffffffffu-length||(op==MemoryOp::Copy16&&((sourceOrValue&1)||sourceOrValue>0xffffffffu-length)))
        throw std::invalid_argument("SDK memory intrinsic range");
    std::uint32_t last=sourceOrValue&65535;
    for(unsigned i=0;i<length;i+=2){
        if(op==MemoryOp::Copy16)last=read(sourceOrValue+i);
        write(destination+i,static_cast<std::uint16_t>(last));
    }
    return {length,last};
}
}
