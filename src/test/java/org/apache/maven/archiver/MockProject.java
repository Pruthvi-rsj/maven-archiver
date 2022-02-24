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
 *  http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.List;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Dependency;
import org.apache.maven.api.Project;
import org.apache.maven.model.Model;

class MockProject implements Project
{
    private Model model;
    private Path pomPath;
    private Artifact artifact;

    public MockProject( Model model )
    {
        this.model = model;
        MockArtifact artifact = new MockArtifact();
        artifact.setGroupId( model.getGroupId() );
        artifact.setArtifactId( model.getArtifactId() );
        artifact.setVersion( model.getVersion() );
        artifact.setExtension( model.getPackaging() );
        this.artifact = artifact;
    }

    @Nonnull
    @Override
    public String getGroupId()
    {
        return model.getGroupId();
    }

    @Nonnull
    @Override
    public String getArtifactId()
    {
        return model.getArtifactId();
    }

    @Nonnull
    @Override
    public String getVersion()
    {
        return model.getVersion();
    }

    @Nonnull
    @Override
    public String getPackaging()
    {
        return null;
    }

    @Nonnull
    @Override
    public Artifact getArtifact()
    {
        return artifact;
    }

    @Nonnull
    @Override
    public Model getModel()
    {
        return model;
    }

    @Nonnull
    @Override
    public Path getPomPath()
    {
        return pomPath;
    }

    @Nonnull
    @Override
    public List<Dependency> getDependencies()
    {
        return null;
    }

    @Nonnull
    @Override
    public List<Dependency> getManagedDependencies()
    {
        return null;
    }

    public void setPomPath( Path pomPath )
    {
        this.pomPath = pomPath;
    }

    public void setArtifact( Artifact artifact )
    {
        this.artifact = artifact;
    }
}
