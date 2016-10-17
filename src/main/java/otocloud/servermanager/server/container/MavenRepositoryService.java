package otocloud.servermanager.server.container;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.repository.AuthenticationBuilder;

public class MavenRepositoryService {

	public static final String HTTP_PROXY_SYS_PROP = "vertx.maven.httpProxy";
	public static final String HTTPS_PROXY_SYS_PROP = "vertx.maven.httpsProxy";
	public static final String LOCAL_REPO_SYS_PROP = "vertx.maven.localRepo";
	public static final String REMOTE_REPOS_SYS_PROP = "vertx.maven.remoteRepos";
	public static final String REMOTE_SNAPSHOT_POLICY_SYS_PROP = "vertx.maven.remoteSnapshotPolicy";
	private static final String DEFAULT_MAVEN_REMOTES = "http://maven.yonyou.com/nexus/content/groups/otocloud-all/";
	private static final String USER_HOME = System.getProperty("user.home");
	private static final String FILE_SEP = System.getProperty("file.separator");
	private static final String DEFAULT_MAVEN_LOCAL = USER_HOME + FILE_SEP + ".m2" + FILE_SEP + "repository";

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	private Proxy proxy;
	private Proxy secureProxy;
	private RepositorySystem system;

	private String localMavenRepo;
	private List<String> remoteMavenRepos;

	public MavenRepositoryService() {
		init();
	}

	private void init() {
		// init RepositorySystem
		DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
		locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
		locator.addService(TransporterFactory.class, FileTransporterFactory.class);
		locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
		locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
			@Override
			public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
				logger.error(exception.getMessage(), exception);
			}
		});
		system = locator.getService(RepositorySystem.class);
		// init Proxy
		String httpProxy = System.getProperty(HTTP_PROXY_SYS_PROP);
		String httpsProxy = System.getProperty(HTTPS_PROXY_SYS_PROP);
		try {
			if (httpProxy != null) {
				URL url = new URL(httpProxy);
				Authentication authentication = extractAuth(url);
				proxy = new Proxy("http", url.getHost(), url.getPort(), authentication);
			}
			if (httpsProxy != null) {
				URL url = new URL(httpsProxy);
				Authentication authentication = extractAuth(url);
				secureProxy = new Proxy("https", url.getHost(), url.getPort(), authentication);
			}
		} catch (MalformedURLException e) {
			logger.error(e.getMessage(), e);
			throw new RuntimeException(e);
		}
	}

	public void setLocalMavenRepo(String localRepo) {
		this.localMavenRepo = localRepo;
	}

	private LocalRepository getLocalRepository() {
		if (localMavenRepo == null) {
			localMavenRepo = System.getProperty(LOCAL_REPO_SYS_PROP, DEFAULT_MAVEN_LOCAL);
		}
		return new LocalRepository(localMavenRepo);
	}

	public void setRemoteMavenRepos(String... remoteRepos) {
		remoteMavenRepos = new ArrayList<String>();
		remoteMavenRepos.addAll(Arrays.asList(remoteRepos));
	}

	private List<RemoteRepository> getRemoteRepositories() {
		int count = 0;
		List<RemoteRepository> remotes = new ArrayList<>();
		if (remoteMavenRepos == null) {
			String remoteString = System.getProperty(REMOTE_REPOS_SYS_PROP, DEFAULT_MAVEN_REMOTES);
			// They are space delimited (space is illegal char in urls)
			remoteMavenRepos = Arrays.asList(remoteString.split(" "));
		}
		for (String remote : remoteMavenRepos) {
			URL url;
			try {
				url = new URL(remote);
				Authentication auth = extractAuth(url);
				if (auth != null) {
					url = new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getFile());
				}

				RemoteRepository.Builder builder = new RemoteRepository.Builder("repo" + (count++), "default",
						url.toString());
				if (auth != null) {
					builder.setAuthentication(auth);
				}
				switch (url.getProtocol()) {
				case "http":
					if (proxy != null) {
						builder.setProxy(proxy);
					}
					break;
				case "https":
					if (secureProxy != null) {
						builder.setProxy(secureProxy);
					}
					break;
				}
				customizeRemoteRepoBuilder(builder);
				RemoteRepository remoteRepo = builder.build();
				remotes.add(remoteRepo);
			} catch (MalformedURLException e) {
				logger.error(e.getMessage(), e);
				continue;
			}
		}
		return remotes;
	}

	/**
	 * 获取资源
	 * 
	 * @param groupId
	 * @param artifactId
	 * @param extension
	 * @param version
	 * @return
	 */
	public ArtifactResult resolveArtifact(String groupId, String artifactId, String extension, String version) {
		Artifact protoArtifact = new DefaultArtifact(groupId, artifactId, extension, version);
		return resolveArtifact(protoArtifact);
	}

	/**
	 * 获取资源
	 * 
	 * @param coords
	 * @return
	 */
	public ArtifactResult resolveArtifact(String coords) {
		Artifact protoArtifact = new DefaultArtifact(coords);
		return resolveArtifact(protoArtifact);
	}

	public ArtifactResult resolveArtifact(Artifact protoArtifact) {
		DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
		session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, getLocalRepository()));
		ArtifactRequest artifactRequest = new ArtifactRequest(protoArtifact, getRemoteRepositories(), null);
		ArtifactResult artifactResult = null;
		try {
			artifactResult = system.resolveArtifact(session, artifactRequest);
		} catch (ArtifactResolutionException e) {
			logger.error(e.getMessage(), e);
		}
		return artifactResult;
	}

	protected void customizeRemoteRepoBuilder(RemoteRepository.Builder builder) {
		String updatePolicy = System.getProperty(REMOTE_SNAPSHOT_POLICY_SYS_PROP);
		if (updatePolicy != null && !updatePolicy.isEmpty()) {
			builder.setSnapshotPolicy(new RepositoryPolicy(true, updatePolicy, RepositoryPolicy.CHECKSUM_POLICY_WARN));
		}
	}

	private static Authentication extractAuth(URL url) {
		String userInfo = url.getUserInfo();
		if (userInfo != null) {
			AuthenticationBuilder authBuilder = new AuthenticationBuilder();
			int sep = userInfo.indexOf(':');
			if (sep != -1) {
				authBuilder.addUsername(userInfo.substring(0, sep));
				authBuilder.addPassword(userInfo.substring(sep + 1));
			} else {
				authBuilder.addUsername(userInfo);
			}
			return authBuilder.build();
		}
		return null;
	}
}
