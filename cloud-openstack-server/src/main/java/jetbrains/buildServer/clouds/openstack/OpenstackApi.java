package jetbrains.buildServer.clouds.openstack;

import java.util.List;
import java.util.Properties;

import org.jclouds.ContextBuilder;
import org.jclouds.location.reference.LocationConstants;
import org.jclouds.openstack.keystone.config.KeystoneProperties;
import org.jclouds.openstack.neutron.v2.NeutronApi;
import org.jclouds.openstack.neutron.v2.NeutronApiMetadata;
import org.jclouds.openstack.neutron.v2.domain.FloatingIP;
import org.jclouds.openstack.neutron.v2.domain.Network;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.extensions.VolumeAttachmentApi;
import org.jclouds.openstack.nova.v2_0.NovaApiMetadata;
import org.jclouds.openstack.nova.v2_0.domain.Flavor;
import org.jclouds.openstack.nova.v2_0.domain.Image;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.springframework.util.StringUtils;

public class OpenstackApi {

    private final String region;

    private final NeutronApi neutronApi;
    private final NovaApi novaApi;

    public OpenstackApi(String endpointUrl, String identity, String password, String region) {

        // For http content debug during unit tests,
        // - Fill Constants.PROPERTY_LOGGER_WIRE_LOG_SENSITIVE_INFO to true in overrides properties
        // - Add '.modules(ImmutableSet.of(new SLF4JLoggingModule()))' in two ContextBuilder
        // - Update log level to 'DEBUG' in 'log4j.xml'.

        this.region = region;

        final Properties overrides = new Properties();
        final String keyStoneVersion = getKeystoneVersion(endpointUrl);
        final OpenstackIdentity identityObject = new OpenstackIdentity(identity, keyStoneVersion);
        overrides.put(KeystoneProperties.KEYSTONE_VERSION, keyStoneVersion);
        overrides.put(LocationConstants.PROPERTY_ZONES, region);

        if (!StringUtils.isEmpty(identityObject.getTenant())) {
            // Only for keystone v3, for v2 'tenant' is part of Credentials (cf. OpenstackIdentity)
            overrides.put(KeystoneProperties.SCOPE, "project:" + identityObject.getTenant());
        }
        if (!StringUtils.isEmpty(identityObject.getTenantDomain())) {
            overrides.put(KeystoneProperties.PROJECT_DOMAIN_NAME, identityObject.getTenantDomain());
        }

        neutronApi = ContextBuilder.newBuilder(new NeutronApiMetadata()).credentials(identityObject.getCredendials(), password).endpoint(endpointUrl)
                .overrides(overrides).buildApi(NeutronApi.class);

        novaApi = ContextBuilder.newBuilder(new NovaApiMetadata()).endpoint(endpointUrl).credentials(identityObject.getCredendials(), password)
                .overrides(overrides).buildApi(NovaApi.class);
    }

    public String getImageIdByName(String name) {
        List<? extends Image> images = novaApi.getImageApi(region).listInDetail().concat().toList();
        for (Image image : images) {
            if (image.getName().equals(name))
                return image.getId();
        }
        return null;
    }

    public String getFlavorIdByName(String name) {
        List<? extends Flavor> flavors = novaApi.getFlavorApi(region).listInDetail().concat().toList();
        for (Flavor flavor : flavors) {
            if (flavor.getName().equals(name))
                return flavor.getId();
        }
        return null;
    }

    public String getNetworkIdByName(String name) {
        List<? extends Network> networks = neutronApi.getNetworkApi(region).list().concat().toList();
        for (Network network : networks) {
            if (network.getName().equals(name))
                return network.getId();
        }
        return null;
    }

    public ServerApi getNovaServerApi() {
        return novaApi.getServerApi(region);
    }

    public void associateFloatingIp(String serverId, String ip) {
        novaApi.getFloatingIPApi(region).get().addToServer(ip, serverId);
    }

    public String getFloatingIpAvailable() {
        for (FloatingIP ip : neutronApi.getFloatingIPApi(region).list().concat().toList()) {
            if (StringUtils.isEmpty(ip.getFixedIpAddress())) {
                return ip.getFloatingIpAddress();
            }
        }
        return null;
    }

    /**
     * Return keystone version (2 or 3) from endpoint URL
     * 
     * @param url endpoint
     * @return 2 or 3
     */
    protected static String getKeystoneVersion(String url) {
        final String def = "3";
        if (StringUtils.isEmpty(url)) {
            return def;
        }
        int index = url.toLowerCase().lastIndexOf("/v") + 2;
        if (url.length() > index) {
            return url.substring(index, index + 1);
        }
        return def;
    }

}
