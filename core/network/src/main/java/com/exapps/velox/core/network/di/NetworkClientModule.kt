package com.exapps.velox.core.network.di

import com.exapps.velox.core.network.net.FtpClientHolder
import com.exapps.velox.core.network.net.NetworkClient
import com.exapps.velox.core.network.net.SmbClient
import com.exapps.velox.core.network.net.WebDavClient
import com.exapps.velox.core.network.model.NetworkProtocol
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Protocol → client registry (Phase 2 network playback/browsing). */
class NetworkClientRegistry(
    val smb: NetworkClient,
    val ftp: NetworkClient,
    val webDav: NetworkClient,
) {
    operator fun get(protocol: NetworkProtocol): NetworkClient = when (protocol) {
        NetworkProtocol.SMB -> smb
        NetworkProtocol.FTP -> ftp
        NetworkProtocol.WEBDAV -> webDav
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkClientModule {

    @Provides
    @Singleton
    fun provideNetworkClientRegistry(
        smb: SmbClient,
        ftp: FtpClientHolder,
        webDav: WebDavClient,
    ): NetworkClientRegistry = NetworkClientRegistry(smb, ftp, webDav)
}
