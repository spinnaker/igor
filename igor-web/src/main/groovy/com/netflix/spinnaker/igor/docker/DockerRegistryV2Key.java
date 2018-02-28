package com.netflix.spinnaker.igor.docker;

public class DockerRegistryV2Key {
    private static final String VERSION = "v2";
    private final String prefix;
    private final String id;
    private final String account;
    private final String registry;
    private final String tag;

    public DockerRegistryV2Key(String prefix, String id, String account, String registry, String tag) {
        this.prefix = prefix;
        this.id = id;
        this.account = account;
        this.registry = registry;
        this.tag = tag;
    }

    public static String getVersion() {
        return VERSION;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getId() {
        return id;
    }

    public String getAccount() {
        return account;
    }

    public String getRegistry() {
        return registry;
    }

    public String getTag() {
        return tag;
    }

    public String toString() {
        return String.format("%s:v2:%s:%s:%s:%s", prefix, id, account, registry, tag);
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof DockerRegistryV2Key && toString().equals(obj.toString());
    }
}
