package com.netflix.spinnaker.igor.docker;

import com.google.common.base.Splitter;
import com.google.common.base.VerifyException;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.google.common.base.Verify.verify;

import java.util.List;

@Service
public class DockerRegistryKeyFactory {
    private final static String ID = "dockerRegistry";
    private final static Splitter SPLITTER = Splitter.on(':');

    private final IgorConfigurationProperties igorConfigurationProperties;

    @Autowired
    public DockerRegistryKeyFactory(IgorConfigurationProperties igorConfigurationProperties) {
        this.igorConfigurationProperties = igorConfigurationProperties;
    }

    DockerRegistryV1Key parseV1Key(String keyStr) throws DockerRegistryKeyFormatException {
        return parseV1Key(keyStr, true);
    }


    DockerRegistryV1Key parseV1Key(String keyStr, boolean includeRepository) throws DockerRegistryKeyFormatException {
        List<String> splits = SPLITTER.splitToList(keyStr);
        try {
            String prefix = splits.get(0);
            verify(prefix.equals(prefix()), "Expected prefix '%s', found '%s'", prefix(), prefix);

            String id = splits.get(1);
            verify(ID.equals(id), "Expected ID '%s', found '%s'", ID, id);

            String account = splits.get(2);
            verify(!account.isEmpty(), "Empty account string");

            String registry = splits.get(3);
            verify(!registry.isEmpty(), "Empty registry string");

            // the repository URL (typically without "http://"
            // it may contain ':' (e.g. port number), so it may be split across multiple tokens
            String repository = null;
            if (includeRepository) {
                List<String> repoSplits = splits.subList(4, splits.size() - 1);
                repository = String.join(":", repoSplits);
            }

            String tag = splits.get(splits.size() - 1);
            verify(!tag.isEmpty(), "Empty registry string");

            return new DockerRegistryV1Key(prefix, id, account, registry, repository, tag);
        } catch(IndexOutOfBoundsException | VerifyException e) {
            throw new DockerRegistryKeyFormatException(String.format("Could not parse '%s' as a v1 key", keyStr), e);
        }

    }

    DockerRegistryV1Key parseV2Key(String keyStr) throws DockerRegistryKeyFormatException {
        throw new UnsupportedOperationException("parseV2Key not implemented yet");
    }

    DockerRegistryV2Key convert(DockerRegistryV1Key oldKey) {
        return new DockerRegistryV2Key(
            oldKey.getPrefix(),
            oldKey.getId(),
            oldKey.getAccount(),
            oldKey.getRegistry(),
            oldKey.getTag()
        );
    }

    private String prefix() {
        return igorConfigurationProperties.getSpinnaker().getJedis().getPrefix();
    }
}
