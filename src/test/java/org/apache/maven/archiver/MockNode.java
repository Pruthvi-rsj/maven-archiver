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
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Dependency;
import org.apache.maven.api.Exclusion;
import org.apache.maven.api.Node;
import org.apache.maven.api.NodeVisitor;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.Repository;

public class MockNode implements Node, Dependency
{
    Artifact artifact;
    String scope;
    List<Node> children = new ArrayList<>();

    public MockNode( Artifact artifact, String scope )
    {
        this.artifact = artifact;
        this.scope = scope;
    }

    @Nonnull
    @Override
    public String getScope()
    {
        return scope;
    }

    @Nullable
    @Override
    public Boolean getOptional()
    {
        return null;
    }

    @Nonnull
    @Override
    public Collection<Exclusion> getExclusions()
    {
        return null;
    }

    @Override
    public Artifact getArtifact()
    {
        return artifact;
    }

    @Override
    public Dependency getDependency()
    {
        return this;
    }

    @Override
    public List<Node> getChildren()
    {
        return children;
    }

    @Override
    public List<Repository> getRemoteRepositories()
    {
        return null;
    }

    @Override
    public Optional<RemoteRepository> getRepository()
    {
        return Optional.empty();
    }

    @Override
    public boolean accept( NodeVisitor visitor )
    {
        return false;
    }

    @Override
    public Node filter( Predicate<Node> filter )
    {
        return null;
    }
}
