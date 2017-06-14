package org.jfrog.artifactory.client;

import org.jfrog.artifactory.client.model.*;
import org.jfrog.artifactory.client.model.repository.settings.RepositorySettings;
import org.jfrog.artifactory.client.model.repository.settings.impl.GenericRepositorySettingsImpl;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static java.util.Arrays.asList;
import static org.apache.commons.lang.StringUtils.countMatches;
import static org.jfrog.artifactory.client.model.impl.RepositoryTypeImpl.*;
import static org.testng.Assert.*;

/**
 * @author jbaruch
 * @since 30/07/12
 */
public class RepositoryTests extends ArtifactoryTestsBase {
    private static final String NEW_VIRTUAL = "client-test-release";

    private LocalRepository localRepository2;
    private VirtualRepository virtualRepository;

    @BeforeClass
    protected void setUp() throws Exception {
        localRepository2 = artifactory.repositories().builders().localRepositoryBuilder()
                .key(localRepository.getKey() + "2")
                .build();
        virtualRepository = artifactory.repositories().builders().virtualRepositoryBuilder()
                .key(NEW_VIRTUAL)
                .repositories(Arrays.asList(localRepository.getKey(), localRepository2.getKey(), getJCenterRepoName()))
                .build();
    }

    @AfterClass
    protected void tearDown() throws Exception {
        deleteRepoIfExists(localRepository2.getKey());
        deleteRepoIfExists(NEW_VIRTUAL);
    }

    @Test(groups = "repositoryBasics")
    public void testDelete() throws Exception {
        //all the assertions are taken care of in deleteRepoIfExists
        deleteRepoIfExists(localRepository.getKey());
    }

    @Test(groups = "repositoryBasics", dependsOnMethods = "testDelete")
    public void testCreate() throws Exception {
        String result = artifactory.repositories().create(2, localRepository);
        assertTrue(result.startsWith("Successfully created repository"));
        assertTrue(curl(LIST_PATH).contains(localRepository.getKey()));

        result = artifactory.repositories().create(2, localRepository2);
        assertTrue(result.startsWith("Successfully created repository"));
        assertTrue(curl(LIST_PATH).contains(localRepository2.getKey()));

        result = artifactory.repositories().create(2, virtualRepository);
        assertTrue(result.startsWith("Successfully created repository"));
        assertTrue(curl(LIST_PATH).contains(virtualRepository.getKey()));
    }

    @Test(dependsOnMethods = "testCreate")
    public void testCreateDirectory() throws IOException {
        Folder folder = artifactory.repository(localRepository.getKey()).folder("myFolder").create();
        assertEquals("/myFolder/", folder.getPath());
        assertNotNull(folder.getCreated());
    }

    @Test(dependsOnMethods = "testCreate")
    public void testCreateDirectoryWithoutPermissions() throws IOException {
        Artifactory anonymousArtifactory = ArtifactoryClient.create(url);
        try {
            anonymousArtifactory.repository(localRepository.getKey()).folder("myFolder").create();
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Unauthorized"));
        }
    }

    @Test(dependsOnMethods = "testCreate")
    public void testCreateDirectoryWithIllegalName() throws IOException {
        try {
            artifactory.repository(localRepository.getKey()).folder("myFolder?").create();
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Internal Server Error"));
        }
    }

    @Test(dependsOnMethods = "testCreate")
    public void testUpdate() throws Exception {
        RepositorySettings genericRepo = new GenericRepositorySettingsImpl();

        LocalRepository changedRepository = artifactory.repositories().builders().builderFrom(localRepository)
                .description("new_description")
                .repositorySettings(genericRepo)
                .build();

        artifactory.repositories().update(changedRepository);
        assertTrue(curlAndStrip("api/repositories/" + localRepository.getKey()).contains("\"description\":\"new_description\""));
    }

    @Test(dependsOnMethods = "testCreate")
    public void testReplicationStatus() throws Exception {
        assertEquals(artifactory.repository(localRepository.getKey()).replicationStatus().getStatus(), "never_run");
    }

    @Test
    public void testListRemotes() throws Exception {
        List<LightweightRepository> remoteRepositories = artifactory.repositories().list(REMOTE);
        assertNotNull(remoteRepositories);
        String expected = curlAndStrip("api/repositories/?type=remote");
        assertEquals(remoteRepositories.size(), countMatches(expected, "{"));
        LightweightRepository repo = remoteRepositories.get(0);
        assertNotNull(repo);
        assertTrue(LightweightRepository.class.isAssignableFrom(repo.getClass()));
        assertTrue(expected.substring(9).startsWith(repo.getKey()));
        assertEquals(repo.getType().toString(), "remote");
        assertFalse(repo.getUrl().startsWith(url));
    }

    @Test(dependsOnMethods = "testCreate")// to be sure number of repos match
    public void testListLocals() throws Exception {
        List<LightweightRepository> localRepositories = artifactory.repositories().list(LOCAL);
        String expected = curl("api/repositories?type=local");
        assertEquals(localRepositories.size(), countMatches(expected, "{"));
        for (LightweightRepository localRepository : localRepositories) {
            assertTrue(localRepository.getUrl().startsWith(url));
            assertEquals(localRepository.getType().toString(), "local");
        }
    }

    @Test
    public void testListVirtuals() throws Exception {
        List<LightweightRepository> virtualRepositories = artifactory.repositories().list(VIRTUAL);
        assertEquals(virtualRepositories.size(), countMatches(curl("api/repositories?type=virtual"), "{"));
        for (LightweightRepository virtualRepository : virtualRepositories) {
            assertTrue(virtualRepository.getUrl().startsWith(url));
            assertEquals(virtualRepository.getType().toString(), "virtual");
        }
    }

    @Test
    public void testGetRemote() throws Exception {
        String jcenterRepoName = getJCenterRepoName();
        Repository repository = artifactory.repository(jcenterRepoName).get();
        assertNotNull(repository);
        assertTrue(RemoteRepository.class.isAssignableFrom(repository.getClass()));
        RemoteRepository jcenter = (RemoteRepository) repository;
        assertEquals(jcenter.getKey(), jcenterRepoName);
        assertEquals(jcenter.getRclass().toString(), "remote");
        //urls of jcenter are different for aol and standalone
        assertTrue(jcenter.getUrl().equals(JCENTER_URL));
        assertEquals(jcenter.getUsername(), "");
        assertEquals(jcenter.getPassword(), "");
        assertEquals(jcenter.getNotes(), "");
        assertEquals(jcenter.getIncludesPattern(), "**/*");
        assertEquals(jcenter.getExcludesPattern(), "");
        assertFalse(jcenter.isHardFail());
        assertFalse(jcenter.isOffline());
        assertFalse(jcenter.isBlackedOut());
        assertTrue(jcenter.isStoreArtifactsLocally());
        assertEquals(jcenter.getLocalAddress(), "");
        assertFalse(jcenter.isShareConfiguration());
        assertFalse(jcenter.isSynchronizeProperties());
        assertNotNull(jcenter.getPropertySets());
        assertEquals(jcenter.getRepoLayoutRef(), "maven-2-default");
        assertNotNull(jcenter.getContentSync());
        assertNotNull(jcenter.getContentSync().getProperties());
        assertNotNull(jcenter.getContentSync().getStatistics());
        assertNotNull(jcenter.getContentSync().getSource());
    }

    @Test(dependsOnMethods = "testCreate")
    public void testGetLocal() throws Exception {
        Repository repository = artifactory.repository(localRepository.getKey()).get();
        assertNotNull(repository);
        assertTrue(LocalRepository.class.isAssignableFrom(repository.getClass()));
        LocalRepository localRepo = (LocalRepository) repository;
        assertEquals(localRepo.getKey(), localRepository.getKey());
        assertEquals(localRepo.getRclass().toString(), "local");
        assertEquals(localRepo.getDescription(), "new local repository");
        assertEquals(localRepo.getNotes(), "");
        assertEquals(localRepo.getIncludesPattern(), "**/*");
        assertEquals(localRepo.getExcludesPattern(), "");
        assertFalse(localRepo.isBlackedOut());
        List<String> propertySets = localRepo.getPropertySets();
        assertEquals(propertySets.size(), 1);
        assertEquals(propertySets.get(0), ("artifactory"));
        assertEquals(localRepo.getRepoLayoutRef(), "maven-2-default");
    }

    @Test(dependsOnMethods = "testCreate")
    public void testGetVirtual() throws Exception {
        Repository repository = artifactory.repository(virtualRepository.getKey()).get();
        assertNotNull(repository);
        assertTrue(VirtualRepository.class.isAssignableFrom(repository.getClass()));
        VirtualRepository libsReleases = (VirtualRepository) repository;
        assertEquals(libsReleases.getKey(), virtualRepository.getKey());
        assertEquals(libsReleases.getRclass().toString(), "virtual");
        assertEquals(libsReleases.getNotes(), "");
        assertEquals(libsReleases.getIncludesPattern(), "**/*");
        assertEquals(libsReleases.getExcludesPattern(), "");
        assertEquals(libsReleases.getRepositories(), asList(localRepository.getKey(), localRepository2.getKey(), getJCenterRepoName()));
        assertFalse(libsReleases.isArtifactoryRequestsCanRetrieveRemoteArtifacts());
        assertTrue(libsReleases.getRepoLayoutRef() == null || libsReleases.getRepoLayoutRef().isEmpty());
    }

    @Test(dependsOnMethods = "testCreate")
    public void testRepositoryIsFolder() throws IOException {
        try {
            assertTrue(artifactory.repository(localRepository.getKey()).isFolder("myFolder"));
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Internal Server Error"));
        }
    }

    @Test(dependsOnMethods = "testCreate")
    public void testRepositoryExists() throws IOException {
        assertTrue(artifactory.repository(localRepository.getKey()).exists());
        String notExistsRepoName = Long.toString(System.currentTimeMillis());
        assertFalse(artifactory.repository(notExistsRepoName).exists());
    }
}
