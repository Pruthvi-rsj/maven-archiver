package org.apache.maven.archiver;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import javax.annotation.Nonnull;

import java.nio.file.Path;
import java.util.Optional;

import org.apache.maven.api.Artifact;

public class MockArtifact implements Artifact
{
    private String groupId;
    private String artifactId;
    private String version;
    private String baseVersion;
    private String extension;
    private String classifier;
    private Path path;
    private boolean snapshot;

    @Nonnull
    @Override
    public String getGroupId()
    {
        return groupId;
    }

    @Nonnull
    @Override
    public String getArtifactId()
    {
        return artifactId;
    }

    @Nonnull
    @Override
    public String getClassifier()
    {
        return classifier;
    }

    @Nonnull
    @Override
    public String getVersion()
    {
        return version;
    }

    @Nonnull
    @Override
    public String getExtension()
    {
        return extension;
    }

    @Nonnull
    @Override
    public String getBaseVersion()
    {
        if ( baseVersion == null && version != null )
        {
            baseVersion = version;
        }
        return baseVersion;
    }

    @Override
    public boolean isSnapshot()
    {
        return snapshot;
    }

    @Nonnull
    @Override
    public Optional<Path> getPath()
    {
        return Optional.ofNullable( path );
    }

    public void setGroupId( String groupId )
    {
        this.groupId = groupId;
    }

    public void setArtifactId( String artifactId )
    {
        this.artifactId = artifactId;
    }

    public void setVersion( String version )
    {
        this.version = version;
    }

    public void setBaseVersion( String baseVersion )
    {
        this.baseVersion = baseVersion;
    }

    public void setSnapshotVersion( String version, String baseVersion )
    {
        this.snapshot = true;
        this.version = version;
        this.baseVersion = baseVersion;
    }

    public void setExtension( String extension )
    {
        this.extension = extension;
    }

    public void setClassifier( String classifier )
    {
        this.classifier = classifier;
    }

    public void setPath( Path path )
    {
        this.path = path;
    }
}
