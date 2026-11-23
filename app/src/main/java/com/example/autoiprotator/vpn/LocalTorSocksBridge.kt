package com.example.autoiprotator.vpn

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Local SOCKS5 relay bridging tun2socks TCP+UDP traffic into Tor:
 * - TCP CONNECT (0x01) streams tunnel directly into Tor's SocksPort.
 * - UDP ASSOCIATE (0x03) forwards port-53 DNS packets directly into Tor's DNSPort,
 *   preventing DNS leaks while bypassing Tor's lack of SOCKS5 UDP ASSOCIATE.
 */
class LocalTorSocksBridge(
    private val torSocksPort: Int,
    private val torDnsPort: Int
) {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serverSocket = ServerSocket(0, 128, loopback)
    private val openSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val openUdpSockets = ConcurrentHashMap.newKeySet<DatagramSocket>()

    val listenPort: Int = serverSocket.localPort

    private var acceptJob: Job? = null

    fun start() {
        acceptJob = scope.launch {
            while (isActive && !serverSocket.isClosed) {
                try {
                    val client = serverSocket.accept()
                    openSockets.add(client)
                    launch { handleClient(client) }
                } catch (_: Exception) {
                    if (!serverSocket.isClosed) {
                        Log.w(TAG, "SOCKS bridge accept interrupted")
                    }
                }
            }
        }
    }

    fun stop() {
        runCatching { serverSocket.close() }
        openSockets.forEach { runCatching { it.close() } }
        openSockets.clear()
        openUdpSockets.forEach { runCatching { it.close() } }
        openUdpSockets.clear()
        scope.cancel()
    }

    private fun handleClient(client: Socket) {
        try {
            client.tcpNoDelay = true
            val clientIn = DataInputStream(client.getInputStream())
            val clientOut = client.getOutputStream()

            // 1. Client greeting: VER | NMETHODS | METHODS
            val ver = clientIn.readUnsignedByte()
            if (ver != 5) {
                client.close()
                return
            }
            val nMethods = clientIn.readUnsignedByte()
            val methods = ByteArray(nMethods)
            clientIn.readFully(methods)

            // Reply: No authentication required
            clientOut.write(byteArrayOf(0x05, 0x00))
            clientOut.flush()

            // 2. Client request: VER | CMD | RSV | ATYP | DST.ADDR | DST.PORT
            val reqVer = clientIn.readUnsignedByte()
            val cmd = clientIn.readUnsignedByte()
            val rsv = clientIn.readUnsignedByte()
            val atyp = clientIn.readUnsignedByte()
            val addrBytes = readSocksAddressBytes(clientIn, atyp)
            val portBytes = ByteArray(2).also { clientIn.readFully(it) }

            if (reqVer != 5) {
                client.close()
                return
            }

            when (cmd) {
                0x01 -> handleTcpConnect(
                    client = client,
                    clientIn = clientIn,
                    clientOut = clientOut,
                    rsv = rsv,
                    atyp = atyp,
                    addrBytes = addrBytes,
                    portBytes = portBytes
                )
                0x03 -> handleUdpAssociate(
                    client = client,
                    clientIn = clientIn,
                    clientOut = clientOut
                )
                else -> {
                    clientOut.write(
                        byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0)
                    )
                    clientOut.flush()
                    client.close()
                }
            }
        } catch (_: Exception) {
            runCatching { client.close() }
        } finally {
            openSockets.remove(client)
        }
    }

    private fun handleTcpConnect(
        client: Socket,
        clientIn: InputStream,
        clientOut: OutputStream,
        rsv: Int,
        atyp: Int,
        addrBytes: ByteArray,
        portBytes: ByteArray
    ) {
        val torSocket = Socket()
        openSockets.add(torSocket)
        try {
            torSocket.tcpNoDelay = true
            torSocket.connect(InetSocketAddress(loopback, torSocksPort), 15_000)

            val torIn = DataInputStream(torSocket.getInputStream())
            val torOut = torSocket.getOutputStream()

            // Handshake with Tor SOCKS5
            torOut.write(byteArrayOf(0x05, 0x01, 0x00))
            torOut.flush()
            val greetingReply = ByteArray(2)
            torIn.readFully(greetingReply)
            if (greetingReply[0].toInt() != 5 || greetingReply[1].toInt() != 0) {
                throw IllegalStateException("Tor SOCKS5 rejected greeting")
            }

            // Forward exact CONNECT request
            val reqPacket = ByteArray(4 + addrBytes.size + 2)
            reqPacket[0] = 0x05
            reqPacket[1] = 0x01
            reqPacket[2] = rsv.toByte()
            reqPacket[3] = atyp.toByte()
            System.arraycopy(addrBytes, 0, reqPacket, 4, addrBytes.size)
            System.arraycopy(portBytes, 0, reqPacket, 4 + addrBytes.size, 2)
            torOut.write(reqPacket)
            torOut.flush()

            // Relay Tor SOCKS5 CONNECT reply back to tun2socks
            val repHead = ByteArray(4)
            torIn.readFully(repHead)
            val repAtyp = repHead[3].toInt() and 0xFF
            val repAddr = readSocksAddressBytes(torIn, repAtyp)
            val repPort = ByteArray(2).also { torIn.readFully(it) }

            val fullReply = ByteArray(4 + repAddr.size + 2)
            System.arraycopy(repHead, 0, fullReply, 0, 4)
            System.arraycopy(repAddr, 0, fullReply, 4, repAddr.size)
            System.arraycopy(repPort, 0, fullReply, 4 + repAddr.size, 2)
            clientOut.write(fullReply)
            clientOut.flush()

            if (repHead[1].toInt() != 0) {
                torSocket.close()
                client.close()
                return
            }

            val uploadJob = scope.launch {
                pipeStream(clientIn, torOut)
                runCatching { torSocket.shutdownOutput() }
            }
            pipeStream(torIn, clientOut)
            runCatching { client.shutdownOutput() }
            uploadJob.cancel()
        } finally {
            openSockets.remove(torSocket)
            runCatching { torSocket.close() }
            runCatching { client.close() }
        }
    }

    private fun handleUdpAssociate(
        client: Socket,
        clientIn: InputStream,
        clientOut: OutputStream
    ) {
        val udpSocket = DatagramSocket(0, loopback)
        openUdpSockets.add(udpSocket)
        val udpPort = udpSocket.localPort

        try {
            clientOut.write(
                byteArrayOf(
                    0x05,
                    0x00,
                    0x00,
                    0x01,
                    127,
                    0,
                    0,
                    1,
                    ((udpPort ushr 8) and 0xFF).toByte(),
                    (udpPort and 0xFF).toByte()
                )
            )
            clientOut.flush()

            val relayJob = scope.launch {
                val recvBuf = ByteArray(4096)
                while (isActive && !udpSocket.isClosed) {
                    try {
                        val packet = DatagramPacket(recvBuf, recvBuf.size)
                        udpSocket.receive(packet)
                        val len = packet.length
                        val data = packet.data.copyOfRange(0, len)
                        val clientAddr = packet.socketAddress
                        val parsed = parseUdpSocksHeader(data, len) ?: continue
                        val (headerLen, dstPort) = parsed
                        if (dstPort == 53 && len > headerLen) {
                            val socksHeader = data.copyOfRange(0, headerLen)
                            val dnsPayload = data.copyOfRange(headerLen, len)
                            launch {
                                forwardDnsToTor(
                                    udpSocket = udpSocket,
                                    clientAddr = clientAddr,
                                    socksHeader = socksHeader,
                                    dnsPayload = dnsPayload
                                )
                            }
                        }
                    } catch (_: Exception) {
                        if (!udpSocket.isClosed) {
                            Log.w(TAG, "UDP relay receive ended")
                        }
                    }
                }
            }

            // SOCKS5 requires UDP association stays open until TCP control socket closes
            val buf = ByteArray(64)
            while (clientIn.read(buf) != -1) {
                // Keep-alive drain
            }
            relayJob.cancel()
        } finally {
            openUdpSockets.remove(udpSocket)
            runCatching { udpSocket.close() }
            runCatching { client.close() }
        }
    }

    private fun forwardDnsToTor(
        udpSocket: DatagramSocket,
        clientAddr: java.net.SocketAddress,
        socksHeader: ByteArray,
        dnsPayload: ByteArray
    ) {
        val dnsSocket = DatagramSocket()
        try {
            dnsSocket.soTimeout = 5_500
            val outPacket = DatagramPacket(dnsPayload, dnsPayload.size, loopback, torDnsPort)
            dnsSocket.send(outPacket)

            val respBuf = ByteArray(4096)
            val respPacket = DatagramPacket(respBuf, respBuf.size)
            dnsSocket.receive(respPacket)

            val wrapped = ByteArray(socksHeader.size + respPacket.length)
            System.arraycopy(socksHeader, 0, wrapped, 0, socksHeader.size)
            System.arraycopy(respPacket.data, 0, wrapped, socksHeader.size, respPacket.length)
            if (!udpSocket.isClosed) {
                udpSocket.send(DatagramPacket(wrapped, wrapped.size, clientAddr))
            }
        } catch (_: Exception) {
            // Timed out or interrupted; caller will retry DNS cleanly
        } finally {
            runCatching { dnsSocket.close() }
        }
    }

    private fun readSocksAddressBytes(input: DataInputStream, atyp: Int): ByteArray = when (atyp) {
        0x01 -> ByteArray(4).also { input.readFully(it) }
        0x03 -> {
            val len = input.readUnsignedByte()
            ByteArray(1 + len).also { arr ->
                arr[0] = len.toByte()
                input.readFully(arr, 1, len)
            }
        }
        0x04 -> ByteArray(16).also { input.readFully(it) }
        else -> throw IllegalArgumentException("Unsupported SOCKS5 ATYP: $atyp")
    }

    private fun parseUdpSocksHeader(data: ByteArray, len: Int): Pair<Int, Int>? {
        if (len < 10) return null
        val frag = data[2].toInt() and 0xFF
        if (frag != 0) return null
        val atyp = data[3].toInt() and 0xFF
        val addrEnd = when (atyp) {
            0x01 -> 4 + 4
            0x03 -> {
                val domLen = data[4].toInt() and 0xFF
                4 + 1 + domLen
            }
            0x04 -> 4 + 16
            else -> return null
        }
        val headerEnd = addrEnd + 2
        if (len < headerEnd) return null
        val dstPort = ((data[addrEnd].toInt() and 0xFF) shl 8) or (data[addrEnd + 1].toInt() and 0xFF)
        return headerEnd to dstPort
    }

    private fun pipeStream(input: InputStream, output: OutputStream) {
        val buf = ByteArray(16 * 1024)
        try {
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                output.write(buf, 0, read)
                output.flush()
            }
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val TAG = "LocalTorSocksBridge"
    }
}
