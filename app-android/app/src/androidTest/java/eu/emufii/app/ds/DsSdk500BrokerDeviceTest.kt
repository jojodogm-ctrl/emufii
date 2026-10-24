package eu.emufii.app.ds

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real ARM64 JNI constructor/ownership check. No NDS, ROM or gameplay claim. */
@RunWith(AndroidJUnit4::class)
class DsSdk500BrokerDeviceTest {
    @Test fun sdk500NativeBrokerLoadsAndOwnsExactlyOneCohort() {
        val type=Class.forName("com.mpds.online.HostBrokerBridge")
        val bridge=type.getDeclaredField("INSTANCE").get(null)
        val create=type.getDeclaredMethod("createSdkBlocks",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Int::class.javaPrimitiveType)
        val attach=type.getDeclaredMethod("attach",Long::class.javaPrimitiveType)
        val close=type.getDeclaredMethod("close",Long::class.javaPrimitiveType)
        assertEquals(0L,create.invoke(bridge,2,0,1) as Long)
        val ticket=create.invoke(bridge,2,0,40) as Long
        assertTrue(ticket>0)
        try {
            assertEquals(0L,create.invoke(bridge,2,0,40) as Long)
            val p1=attach.invoke(bridge,ticket) as Long
            val p2=attach.invoke(bridge,ticket) as Long
            assertTrue(p1>0&&p2>0&&p1!=p2)
        } finally {close.invoke(bridge,ticket)}
        val next=create.invoke(bridge,2,0,40) as Long
        assertTrue(next>ticket)
        close.invoke(bridge,next)
    }
}
