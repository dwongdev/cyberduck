package ch.cyberduck.core.s3;

/*
 * Copyright (c) 2002-2013 David Kocher. All rights reserved.
 * http://cyberduck.ch/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Bug fixes, suggestions and comments should be sent to feedback@cyberduck.ch
 */

import ch.cyberduck.core.*;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.exception.NotfoundException;
import ch.cyberduck.core.features.Delete;
import ch.cyberduck.core.features.Home;
import ch.cyberduck.core.proxy.DisabledProxyFinder;
import ch.cyberduck.core.shared.DefaultHomeFinderService;
import ch.cyberduck.core.ssl.DefaultTrustManagerHostnameCallback;
import ch.cyberduck.core.ssl.DisabledX509TrustManager;
import ch.cyberduck.core.ssl.KeychainX509KeyManager;
import ch.cyberduck.core.ssl.KeychainX509TrustManager;
import ch.cyberduck.core.transfer.TransferStatus;
import ch.cyberduck.test.IntegrationTest;

import org.jets3t.service.impl.rest.httpclient.RegionEndpointCache;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

@Category(IntegrationTest.class)
public class S3ObjectListServiceTest extends AbstractS3Test {

    @Test
    public void testList() throws Exception {
        final Path bucket = new Path("test-eu-central-1-cyberduck", EnumSet.of(Path.Type.directory, Path.Type.volume));
        assertNotSame(AttributedList.EMPTY, new S3ObjectListService(session, new S3AccessControlListFeature(session)).list(
                bucket, new DisabledListProgressListener()));
    }

    @Test
    public void testListVirtualHostStyle() throws Exception {
        final Path bucket = Home.root();
        assertNotSame(AttributedList.EMPTY, new S3ObjectListService(virtualhost, new S3AccessControlListFeature(virtualhost)).list(
                bucket, new DisabledListProgressListener()));
    }

    @Test
    public void testDirectory() throws Exception {
        final Path bucket = new Path("test-eu-central-1-cyberduck", EnumSet.of(Path.Type.directory, Path.Type.volume));
        final S3AccessControlListFeature acl = new S3AccessControlListFeature(session);
        final Path directory = new S3DirectoryFeature(session, new S3WriteFeature(session, acl), acl).mkdir(new Path(bucket, new AlphanumericRandomStringService().random(), EnumSet.of(Path.Type.directory)), new TransferStatus());
        final S3ObjectListService feature = new S3ObjectListService(session, acl);
        assertTrue(feature.list(bucket, new DisabledListProgressListener()).contains(directory));
        final AtomicBoolean callback = new AtomicBoolean();
        assertTrue(feature.list(directory, new DisabledListProgressListener() {
            @Override
            public void chunk(final Path parent, final AttributedList<Path> list) {
                assertNotSame(AttributedList.EMPTY, list);
                callback.set(true);
            }
        }).isEmpty());
        assertTrue(callback.get());
        new S3DefaultDeleteFeature(session, acl).delete(Collections.singletonList(directory), new DisabledLoginCallback(), new Delete.DisabledCallback());
        assertFalse(feature.list(bucket, new DisabledListProgressListener()).contains(directory));
        assertThrows(NotfoundException.class, () -> feature.list(directory, new DisabledListProgressListener()));
    }

    @Test
    public void testListNotFoundFolder() throws Exception {
        final Path container = new Path("test-eu-central-1-cyberduck", EnumSet.of(Path.Type.volume, Path.Type.directory));
        final String name = new AlphanumericRandomStringService().random();
        final S3AccessControlListFeature acl = new S3AccessControlListFeature(session);
        assertThrows(NotfoundException.class, () -> new S3ObjectListService(session, acl).list(new Path(container, name, EnumSet.of(Path.Type.directory)), new DisabledListProgressListener()));
        final Path file = new S3TouchFeature(session, acl).touch(new Path(container, String.format("%s-", name), EnumSet.of(Path.Type.file)), new TransferStatus());
        assertThrows(NotfoundException.class, () -> new S3ObjectListService(session, acl).list(new Path(container, name, EnumSet.of(Path.Type.directory)), new DisabledListProgressListener()));
        new S3DefaultDeleteFeature(session, acl).delete(Collections.singletonList(file), new DisabledLoginCallback(), new Delete.DisabledCallback());
    }

    @Test
    public void testListFile() throws Exception {
        final Path container = new Path("test-eu-central-1-cyberduck", EnumSet.of(Path.Type.volume, Path.Type.directory));
        final String name = new AlphanumericRandomStringService().random();
        final S3AccessControlListFeature acl = new S3AccessControlListFeature(session);
        final Path file = new S3TouchFeature(session, acl).touch(new Path(container, name, EnumSet.of(Path.Type.file)), new TransferStatus());
        assertTrue(new S3ObjectListService(session, acl).list(container, new DisabledListProgressListener()).contains(file));
        assertThrows(NotfoundException.class, () -> new S3ObjectListService(session, acl).list(new Path(container, name, EnumSet.of(Path.Type.directory)), new DisabledListProgressListener()));
        new S3DefaultDeleteFeature(session, acl).delete(Collections.singletonList(file), new DisabledLoginCallback(), new Delete.DisabledCallback());
    }

    @Test(expected = NotfoundException.class)
    public void testListNotFoundFolderMinio() throws Exception {
        final Host host = new Host(new S3Protocol(), "play.min.io", new Credentials(
                "Q3AM3UQ867SPQQA43P2F", "zuf+tfteSlswRu7BJ86wekitnifILbZam1KYY3TG"
        )) {
            @Override
            public String getProperty(final String key) {
                if("s3.bucket.virtualhost.disable".equals(key)) {
                    return String.valueOf(true);
                }
                return super.getProperty(key);
            }
        };
        final S3Session session = new S3Session(host);
        final LoginConnectionService login = new LoginConnectionService(new DisabledLoginCallback(), new DisabledHostKeyCallback(),
                new DisabledPasswordStore(), new DisabledProgressListener());
        login.check(session, new DisabledCancelCallback());
        final S3AccessControlListFeature acl = new S3AccessControlListFeature(session);
        final Path directory = new S3DirectoryFeature(session, new S3WriteFeature(session, acl), acl).mkdir(
                new Path(new DefaultHomeFinderService(session).find(), new AsciiRandomStringService().random(), EnumSet.of(Path.Type.directory, Path.Type.volume)), new TransferStatus());
        try {
            new S3ObjectListService(session, acl).list(new Path(directory, new AsciiRandomStringService(30).random(), EnumSet.of(Path.Type.directory)), new DisabledListProgressListener());
        }
        finally {
            new S3DefaultDeleteFeature(session, acl).delete(Collections.singletonList(directory), new DisabledLoginCallback(), new Delete.DisabledCallback());
        }
    }

    @Test(expected = NotfoundException.class)
    public void testListNotFoundBucket() throws Exception {
        final Path container = new Path("notfound.cyberduck.ch", EnumSet.of(Path.Type.volume, Path.Type.directory));
        new S3ObjectListService(session, new S3AccessControlListFeature(session)).list(container, new DisabledListProgressListener());
    }

    @Test
    public void testListEncodedCharacter() throws Exception {
        final Path container = new Path("test-eu-central-1-cyberduck", EnumSet.of(Path.Type.directory, Path.Type.volume));
        final S3AccessControlListFeature acl = new S3AccessControlListFeature(session);
        final Path placeholder = new S3TouchFeature(session, acl).touch(
                new Path(container, String.format("^<%%%s", new AlphanumericRandomStringService().random()), EnumSet.of(Path.Type.file)), new TransferStatus());
        assertTrue(new S3ObjectListService(session, acl).list(container, new DisabledListProgressListener()).contains(placeholder));
        new S3DefaultDeleteFeature(session, acl).delete(Collections.singletonList(placeholder), new DisabledLoginCallback(), new Delete.DisabledCallback());
    }

    @Test
    public void testListInvisibleCharacter() throws Exception {
        final Path container = new Path("test-eu-central-1-cyberduck", EnumSet.of(Path.Type.directory, Path.Type.volume));
        final S3AccessControlListFeature acl = new S3AccessControlListFeature(session);
        final Path placeholder = new S3TouchFeature(session, acl).touch(
                new Path(container, String.format("test-\u001F-%s", new AlphanumericRandomStringService().random()), EnumSet.of(Path.Type.file)), new TransferStatus());
        assertTrue(new S3ObjectListService(session, acl).list(container, new DisabledListProgressListener()).contains(placeholder));
        new S3DefaultDeleteFeature(session, acl).delete(Collections.singletonList(placeholder), new DisabledLoginCallback(), new Delete.DisabledCallback());
    }

    @Test
    public void testListPlaceholder() throws Exception {
        final Path container = new Path("test-eu-central-1-cyberduck", EnumSet.of(Path.Type.directory, Path.Type.volume));
        final S3AccessControlListFeature acl = new S3AccessControlListFeature(session);
        final Path placeholder = new S3DirectoryFeature(session, new S3WriteFeature(session, acl), acl).mkdir(
                new Path(container, UUID.randomUUID().toString(), EnumSet.of(Path.Type.directory)), new TransferStatus());
        final AttributedList<Path> list = new S3ObjectListService(session, acl).list(placeholder, new DisabledListProgressListener());
        assertTrue(list.isEmpty());
        new S3DefaultDeleteFeature(session, acl).delete(Collections.singletonList(placeholder), new DisabledLoginCallback(), new Delete.DisabledCallback());
    }

    @Test
    public void testListPlaceholderTilde() throws Exception {
        final Path container = new Path("test-eu-central-1-cyberduck", EnumSet.of(Path.Type.directory, Path.Type.volume));
        final S3AccessControlListFeature acl = new S3AccessControlListFeature(session);
        final Path placeholderTildeEnd = new S3DirectoryFeature(session, new S3WriteFeature(session, acl), acl).mkdir(
                new Path(container, String.format("%s~", new AlphanumericRandomStringService().random()), EnumSet.of(Path.Type.directory)), new TransferStatus());
        assertTrue(new S3ObjectListService(session, acl).list(container, new DisabledListProgressListener()).contains(placeholderTildeEnd));
        final Path placeholderTildeStart = new S3DirectoryFeature(session, new S3WriteFeature(session, acl), acl).mkdir(
                new Path(container, String.format("~%s", new AlphanumericRandomStringService().random()), EnumSet.of(Path.Type.directory)), new TransferStatus());
        assertTrue(new S3ObjectListService(session, acl).list(container, new DisabledListProgressListener()).contains(placeholderTildeStart));
        assertTrue(new S3ObjectListService(session, acl).list(placeholderTildeEnd, new DisabledListProgressListener()).isEmpty());
        assertTrue(new S3ObjectListService(session, acl).list(placeholderTildeStart, new DisabledListProgressListener()).isEmpty());
        new S3DefaultDeleteFeature(session, acl).delete(Arrays.asList(placeholderTildeEnd, placeholderTildeStart), new DisabledLoginCallback(), new Delete.DisabledCallback());
    }

    @Test
    public void testListFileTilde() throws Exception {
        final Path container = new Path("test-eu-central-1-cyberduck", EnumSet.of(Path.Type.directory, Path.Type.volume));
        final S3AccessControlListFeature acl = new S3AccessControlListFeature(session);
        final Path file = new S3TouchFeature(session, acl).touch(
                new Path(container, String.format("@%s", new AlphanumericRandomStringService().random()), EnumSet.of(Path.Type.file)), new TransferStatus());
        assertTrue(new S3ObjectListService(session, acl).list(container, new DisabledListProgressListener()).contains(file));
        new S3DefaultDeleteFeature(session, acl).delete(Collections.singletonList(file), new DisabledLoginCallback(), new Delete.DisabledCallback());
    }

    @Test
    public void testListPlaceholderAtSignSignatureAWS4() throws Exception {
        final Path container = new Path("test-eu-central-1-cyberduck", EnumSet.of(Path.Type.directory, Path.Type.volume));
        final S3AccessControlListFeature acl = new S3AccessControlListFeature(session);
        final Path placeholder = new S3DirectoryFeature(session, new S3WriteFeature(session, acl), acl).mkdir(
                new Path(container, String.format("%s@", UUID.randomUUID()), EnumSet.of(Path.Type.directory)), new TransferStatus());
        final AttributedList<Path> list = new S3ObjectListService(session, acl).list(placeholder, new DisabledListProgressListener());
        assertTrue(list.isEmpty());
        new S3DefaultDeleteFeature(session, acl).delete(Collections.singletonList(placeholder), new DisabledLoginCallback(), new Delete.DisabledCallback());
    }

    @Test(expected = BackgroundException.class)
    public void testAccessPathStyleBucketEuCentral() throws Exception {
        final Host host = new Host(new S3Protocol(), new S3Protocol().getDefaultHostname(), new Credentials(
                PROPERTIES.get("s3.key"), PROPERTIES.get("s3.secret")
        ));
        host.setProperty("s3.bucket.virtualhost.disable", String.valueOf(true));
        final S3Session session = new S3Session(host);
        session.open(new DisabledProxyFinder(), new DisabledHostKeyCallback(), new DisabledLoginCallback(), new DisabledCancelCallback());
        session.login(new DisabledLoginCallback(), new DisabledCancelCallback());
        final Path container = new Path("test-eu-central-1-cyberduck", EnumSet.of(Path.Type.volume, Path.Type.directory));
        new S3ObjectListService(session, new S3AccessControlListFeature(session)).list(container, new DisabledListProgressListener());
    }

    @Test
    public void testLaxHostnameVerification() throws Exception {
        final Host host = new Host(new S3Protocol(), new S3Protocol().getDefaultHostname(), new Credentials(
                PROPERTIES.get("s3.key"), PROPERTIES.get("s3.secret")
        ));
        final KeychainX509TrustManager trust = new KeychainX509TrustManager(new DisabledCertificateTrustCallback(), new DefaultTrustManagerHostnameCallback(host),
                new DisabledCertificateStore() {
                    @Override
                    public boolean verify(final CertificateTrustCallback prompt, final String hostname, final List<X509Certificate> certificates) throws CertificateException {
                        assertEquals("ch.s3.amazonaws.com", hostname);
                        return true;
                    }
                });
        final S3Session session = new S3Session(host, new DisabledX509TrustManager(),
                new KeychainX509KeyManager(new DisabledCertificateIdentityCallback(), host, new DisabledCertificateStore()));
        final LoginConnectionService login = new LoginConnectionService(new DisabledLoginCallback() {
            @Override
            public Credentials prompt(final Host bookmark, final String username, final String title, final String reason, final LoginOptions options) {
                fail(reason);
                return null;
            }
        }, new DisabledHostKeyCallback(),
                new DisabledPasswordStore(), new DisabledProgressListener());
        login.check(session, new DisabledCancelCallback());
        new S3ObjectListService(session, new S3AccessControlListFeature(session)).list(
                new Path("test-eu-central-1-cyberduck", EnumSet.of(Path.Type.volume, Path.Type.directory)), new DisabledListProgressListener());
    }

    @Test
    public void testListFilePlusCharacter() throws Exception {
        final Path container = new Path("test-eu-central-1-cyberduck", EnumSet.of(Path.Type.directory, Path.Type.volume));
        final S3AccessControlListFeature acl = new S3AccessControlListFeature(session);
        final Path file = new S3TouchFeature(session, acl).touch(
                new Path(container, String.format("test+%s", new AlphanumericRandomStringService().random()), EnumSet.of(Path.Type.file)), new TransferStatus());
        assertTrue(new S3ObjectListService(session, acl).list(container, new DisabledListProgressListener()).contains(file));
        new S3DefaultDeleteFeature(session, acl).delete(Collections.singletonList(file), new DisabledLoginCallback(), new Delete.DisabledCallback());
    }

    @Test
    public void testListFileDot() throws Exception {
        final Path container = new Path("test-eu-central-1-cyberduck", EnumSet.of(Path.Type.directory, Path.Type.volume));
        final S3AccessControlListFeature acl = new S3AccessControlListFeature(session);
        final Path file = new S3TouchFeature(session, acl).touch(
                new Path(container, ".", EnumSet.of(Path.Type.file)), new TransferStatus());
        assertTrue(new S3ObjectListService(session, acl).list(container, new DisabledListProgressListener()).contains(file));
        new S3DefaultDeleteFeature(session, acl).delete(Collections.singletonList(file), new DisabledLoginCallback(), new Delete.DisabledCallback());
    }

    @Test
    public void testListPlaceholderDot() throws Exception {
        final Path container = new Path("test-eu-central-1-cyberduck", EnumSet.of(Path.Type.directory, Path.Type.volume));
        final S3AccessControlListFeature acl = new S3AccessControlListFeature(session);
        final Path placeholder = new S3DirectoryFeature(session, new S3WriteFeature(session, acl), acl).mkdir(
                new Path(container, ".", EnumSet.of(Path.Type.directory)), new TransferStatus());
        assertTrue(new S3ObjectListService(session, new S3AccessControlListFeature(session)).list(container, new DisabledListProgressListener()).contains(placeholder));
        new S3DefaultDeleteFeature(session, acl).delete(Collections.singletonList(placeholder), new DisabledLoginCallback(), new Delete.DisabledCallback());
    }

    @Test
    public void testListPlaceholderPlusCharacter() throws Exception {
        final Path container = new Path("test-eu-central-1-cyberduck", EnumSet.of(Path.Type.directory, Path.Type.volume));
        final S3AccessControlListFeature acl = new S3AccessControlListFeature(session);
        final Path placeholder = new S3DirectoryFeature(session, new S3WriteFeature(session, acl), acl).mkdir(
                new Path(container, String.format("test+%s", new AlphanumericRandomStringService().random()), EnumSet.of(Path.Type.directory)), new TransferStatus());
        assertTrue(new S3ObjectListService(session, acl).list(container, new DisabledListProgressListener()).contains(placeholder));
        assertTrue(new S3ObjectListService(session, acl).list(placeholder, new DisabledListProgressListener()).isEmpty());
        new S3DefaultDeleteFeature(session, acl).delete(Collections.singletonList(placeholder), new DisabledLoginCallback(), new Delete.DisabledCallback());
    }

    @Test
    public void testDetermineCorrectRegionFrom400Reply() throws Exception {
        final Path bucket = new Path(new DefaultHomeFinderService(session).find(), new AsciiRandomStringService(30).random(), EnumSet.of(Path.Type.directory, Path.Type.volume));
        final S3AccessControlListFeature acl = new S3AccessControlListFeature(session);
        final S3DirectoryFeature feature = new S3DirectoryFeature(session, new S3WriteFeature(session, acl), acl);
        feature.mkdir(bucket, new TransferStatus().setRegion("eu-central-1"));
        // Populate incorrect region in cache
        final RegionEndpointCache cache = session.getClient().getRegionEndpointCache();
        assertEquals("eu-central-1", cache.getRegionForBucketName(bucket.getName()));
        cache.putRegionForBucketName(bucket.getName(), "eu-west-1");
        assertNotSame(AttributedList.emptyList(), new S3ObjectListService(session, acl).list(bucket, new DisabledListProgressListener()));
        assertEquals("eu-central-1", cache.getRegionForBucketName(bucket.getName()));
        assertFalse(session.getClient().getConfiguration().getBoolProperty("s3service.disable-dns-buckets", true));
        new S3DefaultDeleteFeature(session, acl).delete(Collections.singletonList(bucket), new DisabledLoginCallback(), new Delete.DisabledCallback());
    }

    @Test
    public void testListCommonPrefixSlashOnly() throws Exception {
        final Path container = new Path("test-eu-central-1-cyberduck-unsupportedprefix", EnumSet.of(Path.Type.directory, Path.Type.volume));
        final S3AccessControlListFeature acl = new S3AccessControlListFeature(session);
        assertTrue(new S3ObjectListService(session, acl).list(container, new DisabledListProgressListener()).isEmpty());
    }
}
