/* Copyright (c) 2008, Nathan Sweet
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * - Neither the name of Esoteric Software nor the names of its contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */

package in.mcxiv.rmi;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.rmi.RemoteObject;

import java.lang.annotation.Annotation;

public class DelegateObject implements RemoteObject, RMI.RMISupplier {

    RemoteSpace space;
    Connection connection;

    boolean isLocal = false;
    boolean isUdp = false;
    boolean isNonBlocking = false;
    boolean returnsNotRequired = false;
    int responseTimeout = 3000;
    RMI.TransmitExceptions.Transmission transmitExceptions = RMI.TransmitExceptions.Transmission.TO_STRING;
    boolean remoteToString = false;
    boolean remoteHashCode = false;
    boolean closed = false;

    RMI rmi;

    public DelegateObject(RemoteSpace space, Connection connection) {
        this.space = space;
        this.connection = connection;

        this.rmi = new RMI() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return RMI.class;
            }

            @Override
            public boolean local() {
                return isLocal;
            }

            @Override
            public boolean useUdp() {
                return isUdp;
            }

            @Override
            public boolean nonBlocking() {
                return isNonBlocking;
            }

            @Override
            public boolean noReturns() {
                return returnsNotRequired;
            }

            @Override
            public int responseTimeout() {
                return responseTimeout;
            }

            @Override
            public TransmitExceptions.Transmission transmitExceptions() {
                return transmitExceptions;
            }

            @Override
            public boolean delegatedToString() {
                return !remoteToString;
            }

            @Override
            public boolean delegatedHashCode() {
                return !remoteHashCode;
            }

            @Override
            public boolean closed() {
                return closed;
            }
        };
    }

    @Override
    public void setResponseTimeout(int timeoutMillis) {
        responseTimeout = timeoutMillis;
    }

    @Override
    public void setNonBlocking(boolean nonBlocking) {
        isNonBlocking = nonBlocking;
    }

    @Override
    public void setTransmitReturnValue(boolean transmit) {
        returnsNotRequired = !transmit;
    }

    @Override
    public void setTransmitExceptions(boolean transmit) {
        if (transmit) transmitExceptions = RMI.TransmitExceptions.Transmission.TO_STRING;
        else transmitExceptions = RMI.TransmitExceptions.Transmission.LOCAL_ONLY;
    }

    @Override
    public void setUDP(boolean udp) {
        isUdp = udp;
    }

    @Override
    public void setRemoteToString(boolean remoteToString) {
        this.remoteToString = remoteToString;
    }

    public void setRemoteHashCode(boolean remoteHashCode) {
        this.remoteHashCode = remoteHashCode;
    }

    @Override
    public Object waitForLastResponse() {
        return space.getLastResult();
    }

    @Override
    public Object hasLastResponse() {
        return space.hasLastResult();
    }

    @Override
    public byte getLastResponseID() {
        return (byte) space.getLastTransactionId();
    }

    @Override
    public Object waitForResponse(byte responseID) {
        return space.getResult(responseID);
    }

    @Override
    public Object hasResponse(byte responseID) {
        return space.hasResult(responseID);
    }

    @Override
    public void close() {
        closed = true;
    }

    public void open() {
        closed = false;
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public RMI getRMI(RMI defaultRmi) {
        return rmi;
    }
}
