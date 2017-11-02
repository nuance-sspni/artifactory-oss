package org.artifactory.descriptor.repo;

import org.artifactory.descriptor.Descriptor;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

/**
 * @author Shay Bagants
 */
@XmlType(name = "ComposerConfigurationType", propOrder = {"composerRegistryUrl"}, namespace = Descriptor.NS)
public class ComposerConfiguration implements Descriptor {

    @XmlElement(defaultValue = "https://packagist.org", required = false)
    private String composerRegistryUrl = "https://packagist.org";

    public String getComposerRegistryUrl() {
        return composerRegistryUrl;
    }

    public void setComposerRegistryUrl(String composerRegistryUrl) {
        this.composerRegistryUrl = composerRegistryUrl;
    }
}
