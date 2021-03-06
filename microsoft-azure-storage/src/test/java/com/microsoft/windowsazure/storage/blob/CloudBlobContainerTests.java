/**
 * Copyright Microsoft Corporation
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.microsoft.windowsazure.storage.blob;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.microsoft.windowsazure.storage.ResultContinuation;
import com.microsoft.windowsazure.storage.ResultSegment;
import com.microsoft.windowsazure.storage.StorageException;
import com.microsoft.windowsazure.storage.TestRunners.CloudTests;
import com.microsoft.windowsazure.storage.TestRunners.DevFabricTests;
import com.microsoft.windowsazure.storage.TestRunners.DevStoreTests;
import com.microsoft.windowsazure.storage.TestRunners.SlowTests;

/**
 * Blob Container Tests
 */
@Category({ DevFabricTests.class, DevStoreTests.class, CloudTests.class })
public class CloudBlobContainerTests extends BlobTestBase {

    protected static CloudBlobClient client;
    protected CloudBlobContainer container;

    @BeforeClass
    public static void blobContainerTestClassSetUp() {
        client = createCloudBlobClient();
    }

    @Before
    public void blobContainerTestMethodSetUp() throws URISyntaxException, StorageException {
        container = getRandomContainerReference();
    }

    @After
    public void blobContainerTestMethodTearDown() throws StorageException {
        container.deleteIfExists();
    }

    /**
     * Validate container references
     * 
     * @throws StorageException
     * @throws URISyntaxException
     */
    @Test
    public void testCloudBlobContainerReference() throws StorageException, URISyntaxException {

        CloudBlobContainer container = client.getContainerReference("container");
        CloudBlockBlob blockBlob = container.getBlockBlobReference("directory1/blob1");
        CloudPageBlob pageBlob = container.getPageBlobReference("directory2/blob2");
        CloudBlobDirectory directory = container.getDirectoryReference("directory3");
        CloudBlobDirectory directory2 = directory.getSubDirectoryReference("directory4");

        assertEquals(container.getStorageUri().toString(), blockBlob.getContainer().getStorageUri().toString());
        assertEquals(container.getStorageUri().toString(), pageBlob.getContainer().getStorageUri().toString());
        assertEquals(container.getStorageUri().toString(), directory.getContainer().getStorageUri().toString());
        assertEquals(container.getStorageUri().toString(), directory2.getContainer().getStorageUri().toString());
        assertEquals(container.getStorageUri().toString(), directory2.getParent().getContainer().getStorageUri()
                .toString());
        assertEquals(container.getStorageUri().toString(), blockBlob.getParent().getContainer().getStorageUri()
                .toString());
        assertEquals(container.getStorageUri().toString(), blockBlob.getParent().getContainer().getStorageUri()
                .toString());
    }

    /**
     * Create and delete a container
     * 
     * @throws StorageException
     * @throws URISyntaxException
     */
    @Test
    public void testCloudBlobContainerCreate() throws StorageException, URISyntaxException {
        container.create();
        try {
            container.create();
        }
        catch (StorageException e) {
            assertEquals(e.getErrorCode(), "ContainerAlreadyExists");
            assertEquals(e.getHttpStatusCode(), 409);
            assertEquals(e.getMessage(), "The specified container already exists.");
        }
    }

    /**
     * Try to create a container after it is created
     * 
     * @throws StorageException
     * @throws URISyntaxException
     */
    @Test
    public void testCloudBlobContainerCreateIfNotExists() throws StorageException, URISyntaxException {
        assertTrue(container.createIfNotExists());
        assertTrue(container.exists());
        assertFalse(container.createIfNotExists());
    }

    /**
     * Try to delete a non-existing container
     * 
     * @throws URISyntaxException
     * @throws StorageException
     */
    @Test
    public void testCloudBlobContainerDeleteIfExists() throws URISyntaxException, StorageException {
        assertFalse(container.deleteIfExists());
        container.create();
        assertTrue(container.deleteIfExists());
        assertFalse(container.exists());
        assertFalse(container.deleteIfExists());
    }

    /**
     * Check a container's existence
     * 
     * @throws URISyntaxException
     * @throws StorageException
     */
    @Test
    public void testCloudBlobContainerExists() throws URISyntaxException, StorageException {
        assertFalse(container.exists());

        container.create();
        assertTrue(container.exists());
        assertNotNull(container.getProperties().getEtag());

        container.delete();
        assertFalse(container.exists());
    }

    /**
     * Set and delete container permissions
     * 
     * @throws URISyntaxException
     * @throws StorageException
     * @throws InterruptedException
     */
    @Test
    @Category(SlowTests.class)
    public void testCloudBlobContainerSetPermissions() throws URISyntaxException, StorageException,
            InterruptedException {
        container.create();

        BlobContainerPermissions permissions = container.downloadPermissions();
        assertTrue(BlobContainerPublicAccessType.OFF.equals(permissions.getPublicAccess()));
        assertEquals(0, permissions.getSharedAccessPolicies().size());

        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        cal = new GregorianCalendar(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DATE),
                cal.get(Calendar.HOUR), cal.get(Calendar.MINUTE));
        Date start = cal.getTime();
        cal.add(Calendar.MINUTE, 30);
        Date expiry = cal.getTime();

        permissions.setPublicAccess(BlobContainerPublicAccessType.CONTAINER);
        SharedAccessBlobPolicy policy = new SharedAccessBlobPolicy();
        policy.setPermissions(EnumSet.of(SharedAccessBlobPermissions.LIST));
        policy.setSharedAccessStartTime(start);
        policy.setSharedAccessExpiryTime(expiry);
        permissions.getSharedAccessPolicies().put("key1", policy);

        container.uploadPermissions(permissions);
        Thread.sleep(30000);
        // Check if permissions were set
        CloudBlobContainer container2 = client.getContainerReference(container.getName());
        assertPermissionsEqual(permissions, container2.downloadPermissions());

        // Clear permissions
        permissions.getSharedAccessPolicies().clear();
        container.uploadPermissions(permissions);
        Thread.sleep(30000);

        // Check if permissions were cleared
        // Public access should still be the same
        permissions = container2.downloadPermissions();
        assertPermissionsEqual(permissions, container2.downloadPermissions());
    }

    /**
     * Get permissions from string
     * 
     */
    @Test
    public void testCloudBlobContainerPermissionsFromString() {
        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        Date start = cal.getTime();
        cal.add(Calendar.MINUTE, 30);
        Date expiry = cal.getTime();

        SharedAccessBlobPolicy policy = new SharedAccessBlobPolicy();
        policy.setSharedAccessStartTime(start);
        policy.setSharedAccessExpiryTime(expiry);

        policy.setPermissionsFromString("rwdl");
        assertEquals(EnumSet.of(SharedAccessBlobPermissions.READ, SharedAccessBlobPermissions.WRITE,
                SharedAccessBlobPermissions.DELETE, SharedAccessBlobPermissions.LIST), policy.getPermissions());

        policy.setPermissionsFromString("rwl");
        assertEquals(EnumSet.of(SharedAccessBlobPermissions.READ, SharedAccessBlobPermissions.WRITE,
                SharedAccessBlobPermissions.LIST), policy.getPermissions());

        policy.setPermissionsFromString("wr");
        assertEquals(EnumSet.of(SharedAccessBlobPermissions.WRITE, SharedAccessBlobPermissions.READ),
                policy.getPermissions());

        policy.setPermissionsFromString("d");
        assertEquals(EnumSet.of(SharedAccessBlobPermissions.DELETE), policy.getPermissions());
    }

    /**
     * Write permission to string
     */
    @Test
    public void testCloudBlobContainerPermissionsToString() {
        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        Date start = cal.getTime();
        cal.add(Calendar.MINUTE, 30);
        Date expiry = cal.getTime();

        SharedAccessBlobPolicy policy = new SharedAccessBlobPolicy();
        policy.setSharedAccessStartTime(start);
        policy.setSharedAccessExpiryTime(expiry);

        policy.setPermissions(EnumSet.of(SharedAccessBlobPermissions.READ, SharedAccessBlobPermissions.WRITE,
                SharedAccessBlobPermissions.DELETE, SharedAccessBlobPermissions.LIST));
        assertEquals("rwdl", policy.permissionsToString());

        policy.setPermissions(EnumSet.of(SharedAccessBlobPermissions.READ, SharedAccessBlobPermissions.WRITE,
                SharedAccessBlobPermissions.LIST));
        assertEquals("rwl", policy.permissionsToString());

        policy.setPermissions(EnumSet.of(SharedAccessBlobPermissions.WRITE, SharedAccessBlobPermissions.READ));
        assertEquals("rw", policy.permissionsToString());

        policy.setPermissions(EnumSet.of(SharedAccessBlobPermissions.DELETE));
        assertEquals("d", policy.permissionsToString());
    }

    @Test
    public void testCloudBlobContainerUploadMetadata() throws StorageException, URISyntaxException {
        container.create();

        CloudBlobContainer container2 = container.getServiceClient().getContainerReference(container.getName());
        container2.downloadAttributes();
        Assert.assertEquals(0, container2.getMetadata().size());

        container.getMetadata().put("key1", "value1");
        container.uploadMetadata();

        container2.downloadAttributes();
        Assert.assertEquals(1, container2.getMetadata().size());
        Assert.assertEquals("value1", container2.getMetadata().get("key1"));

        Iterable<CloudBlobContainer> containers = container.getServiceClient().listContainers(container.getName(),
                ContainerListingDetails.METADATA, null, null);

        for (CloudBlobContainer container3 : containers) {
            Assert.assertEquals(1, container3.getMetadata().size());
            Assert.assertEquals("value1", container3.getMetadata().get("key1"));
        }

        container.getMetadata().clear();
        container.uploadMetadata();

        container2.downloadAttributes();
        Assert.assertEquals(0, container2.getMetadata().size());
    }

    /**
     * List the blobs in a container
     * 
     * @throws URISyntaxException
     * @throws StorageException
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void testCloudBlobContainerListBlobs() throws URISyntaxException, StorageException, IOException,
            InterruptedException {
        container.create();
        int numBlobs = 200;
        List<String> blobNames = uploadNewBlobs(container, BlobType.BLOCK_BLOB, numBlobs, 128, null);

        assertEquals(numBlobs, blobNames.size());

        int count = 0;
        for (ListBlobItem blob : container.listBlobs()) {
            assertEquals(CloudBlockBlob.class, blob.getClass());
            count++;
        }
        assertEquals(200, count);

        ResultContinuation token = null;

        do {
            ResultSegment<ListBlobItem> result = container.listBlobsSegmented("bb", false,
                    EnumSet.noneOf(BlobListingDetails.class), 150, token, null, null);
            for (ListBlobItem blob : result.getResults()) {
                assertEquals(CloudBlockBlob.class, blob.getClass());
                assertTrue(blobNames.remove(((CloudBlockBlob) blob).getName()));
            }
            token = result.getContinuationToken();
        } while (token != null);

        assertTrue(blobNames.size() == 0);
    }

    /**
     * List the blobs in a container
     * 
     * @throws URISyntaxException
     * @throws StorageException
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void testCloudBlobContainerListBlobsOptions() throws URISyntaxException, StorageException, IOException,
            InterruptedException {
        container.create();
        final int length = 128;

        // regular blob
        CloudBlockBlob originalBlob = (CloudBlockBlob) uploadNewBlob(container, BlobType.BLOCK_BLOB, "originalBlob",
                length, null);

        // copy of regular blob
        CloudBlockBlob copyBlob = container.getBlockBlobReference(generateRandomBlobNameWithPrefix("originalBlobCopy"));
        copyBlob.startCopyFromBlob(originalBlob);
        waitForCopy(copyBlob);

        // snapshot of regular blob
        CloudBlockBlob blobSnapshot = (CloudBlockBlob) originalBlob.createSnapshot();

        // snapshot of the copy of the regular blob
        CloudBlockBlob copySnapshot = container
                .getBlockBlobReference(generateRandomBlobNameWithPrefix("originalBlobSnapshotCopy"));
        copySnapshot.startCopyFromBlob(copyBlob);
        waitForCopy(copySnapshot);

        int count = 0;
        for (ListBlobItem item : container.listBlobs("originalBlob", true, EnumSet.allOf(BlobListingDetails.class),
                null, null)) {
            CloudBlockBlob blob = (CloudBlockBlob) item;
            if (blob.getName().equals(originalBlob.getName()) && !blob.isSnapshot()) {
                assertCreatedAndListedBlobsEquivalent(originalBlob, blob, length);
            }
            else if (blob.getName().equals(copyBlob.getName())) {
                assertCreatedAndListedBlobsEquivalent(copyBlob, blob, length);
            }
            else if (blob.getName().equals(blobSnapshot.getName()) && blob.isSnapshot()) {
                assertCreatedAndListedBlobsEquivalent(blobSnapshot, blob, length);
            }
            else if (blob.getName().equals(copySnapshot.getName())) {
                assertCreatedAndListedBlobsEquivalent(copySnapshot, blob, length);
            }
            else {
                fail("An unexpected blob " + blob.getName() + " was listed.");
            }
            count++;
        }
        assertEquals(4, count);
    }

    /**
     * @throws StorageException
     * @throws URISyntaxException
     * @throws InterruptedException
     */
    @Test
    public void testCloudBlobContainerSharedKeyLite() throws StorageException, URISyntaxException, InterruptedException {
        BlobContainerPermissions expectedPermissions;
        BlobContainerPermissions testPermissions;

        container.create();

        // Test new permissions.
        expectedPermissions = new BlobContainerPermissions();
        testPermissions = container.downloadPermissions();
        assertPermissionsEqual(expectedPermissions, testPermissions);

        // Test setting empty permissions.
        container.uploadPermissions(expectedPermissions);
        testPermissions = container.downloadPermissions();
        assertPermissionsEqual(expectedPermissions, testPermissions);

        // Add a policy, check setting and getting.
        SharedAccessBlobPolicy policy1 = new SharedAccessBlobPolicy();
        Calendar now = Calendar.getInstance();
        policy1.setSharedAccessStartTime(now.getTime());
        now.add(Calendar.MINUTE, 10);
        policy1.setSharedAccessExpiryTime(now.getTime());

        policy1.setPermissions(EnumSet.of(SharedAccessBlobPermissions.READ, SharedAccessBlobPermissions.DELETE,
                SharedAccessBlobPermissions.LIST, SharedAccessBlobPermissions.DELETE));
        expectedPermissions.getSharedAccessPolicies().put(UUID.randomUUID().toString(), policy1);

        container.uploadPermissions(expectedPermissions);
        Thread.sleep(30000);

        testPermissions = container.downloadPermissions();
        assertPermissionsEqual(expectedPermissions, testPermissions);
    }

    // Helper Method
    private static void assertPermissionsEqual(BlobContainerPermissions expected, BlobContainerPermissions actual) {
        assertEquals(expected.getPublicAccess(), actual.getPublicAccess());
        HashMap<String, SharedAccessBlobPolicy> expectedPolicies = expected.getSharedAccessPolicies();
        HashMap<String, SharedAccessBlobPolicy> actualPolicies = actual.getSharedAccessPolicies();
        assertEquals("SharedAccessPolicies.Count", expectedPolicies.size(), actualPolicies.size());
        for (String name : expectedPolicies.keySet()) {
            assertTrue("Key" + name + " doesn't exist", actualPolicies.containsKey(name));
            SharedAccessBlobPolicy expectedPolicy = expectedPolicies.get(name);
            SharedAccessBlobPolicy actualPolicy = actualPolicies.get(name);
            assertEquals("Policy: " + name + "\tPermissions\n", expectedPolicy.getPermissions().toString(),
                    actualPolicy.getPermissions().toString());
            assertEquals("Policy: " + name + "\tStartDate\n", expectedPolicy.getSharedAccessStartTime().toString(),
                    actualPolicy.getSharedAccessStartTime().toString());
            assertEquals("Policy: " + name + "\tExpireDate\n", expectedPolicy.getSharedAccessExpiryTime().toString(),
                    actualPolicy.getSharedAccessExpiryTime().toString());
        }
    }

    /**
     * Checks that a given created blob is listed correctly
     * 
     * @param createdBlob
     * @param listedBlob
     * @param length
     * @throws StorageException
     * @throws URISyntaxException
     */
    private static void assertCreatedAndListedBlobsEquivalent(CloudBlockBlob createdBlob, CloudBlockBlob listedBlob,
            int length) throws StorageException, URISyntaxException {
        assertEquals(createdBlob.getContainer().getName(), listedBlob.getContainer().getName());
        assertEquals(createdBlob.getMetadata(), listedBlob.getMetadata());
        assertEquals(createdBlob.getName(), listedBlob.getName());
        assertEquals(createdBlob.getQualifiedUri(), listedBlob.getQualifiedUri());
        assertEquals(createdBlob.getSnapshotID(), listedBlob.getSnapshotID());
        assertEquals(createdBlob.getUri(), listedBlob.getUri());

        // Compare Properties
        BlobProperties props1 = createdBlob.getProperties();
        BlobProperties props2 = listedBlob.getProperties();
        assertEquals(props1.getBlobType(), props2.getBlobType());
        assertEquals(props1.getContentDisposition(), props2.getContentDisposition());
        assertEquals(props1.getContentEncoding(), props2.getContentEncoding());
        assertEquals(props1.getContentLanguage(), props2.getContentLanguage());

        if (props1.getContentType() == null) {
            assertEquals("application/octet-stream", props2.getContentType());
        }
        else {
            assertEquals(props1.getContentType(), props2.getContentType());
        }

        if (props1.getContentMD5() != null) {
            assertEquals(props1.getContentMD5(), props2.getContentMD5());
        }
        assertEquals(props1.getEtag(), props2.getEtag());

        assertEquals(props1.getLeaseStatus(), props2.getLeaseStatus());

        if (props1.getLeaseState() != null) {
            assertEquals(props1.getLeaseState(), props2.getLeaseState());
        }

        assertEquals(length, props2.getLength());
        assertEquals(props1.getLastModified(), props2.getLastModified());
        assertEquals(props1.getCacheControl(), props2.getCacheControl());

        // Compare CopyState
        CopyState state1 = props1.getCopyState();
        CopyState state2 = props2.getCopyState();
        if (state1 == null && state2 == null) {
            return;
        }
        else {
            assertEquals(new Long(length), state2.getBytesCopied());
            assertNotNull(state2.getCompletionTime());
            assertEquals(state1.getCopyId(), state2.getCopyId());
            assertNotNull(state2.getSource());
            assertEquals(state1.getStatus(), state2.getStatus());
            assertEquals(new Long(length), state2.getTotalBytes());
        }
    }
}
