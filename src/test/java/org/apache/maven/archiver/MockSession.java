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

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;

import org.apache.maven.api.Listener;
import org.apache.maven.api.LocalRepository;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.Session;
import org.apache.maven.api.SessionData;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.api.services.Service;
import org.apache.maven.settings.Settings;

public class MockSession implements Session
{

    private Properties systemProperties;
    private ProjectManager projectManager;

    @Nonnull
    @Override
    public Settings getSettings()
    {
        return null;
    }

    @Nonnull
    @Override
    public LocalRepository getLocalRepository()
    {
        return null;
    }

    @Nonnull
    @Override
    public List<RemoteRepository> getRemoteRepositories()
    {
        return null;
    }

    @Nonnull
    @Override
    public SessionData getData()
    {
        return null;
    }

    @Nonnull
    @Override
    public Properties getUserProperties()
    {
        return null;
    }

    @Nonnull
    @Override
    public Properties getSystemProperties()
    {
        return systemProperties;
    }

    @Nonnull
    @Override
    public <T extends Service> T getService( Class<T> clazz ) throws NoSuchElementException
    {
        if ( clazz == ProjectManager.class )
        {
            return (T) projectManager;
        }
        return null;
    }

    @Nonnull
    @Override
    public Session withLocalRepository( @Nonnull LocalRepository localRepository )
    {
        return null;
    }

    @Nonnull
    @Override
    public Session withRemoteRepositories( @Nonnull List<RemoteRepository> repositories )
    {
        return null;
    }

    @Override
    public void registerListener( @Nonnull Listener listener )
    {

    }

    @Override
    public void unregisterListener( @Nonnull Listener listener )
    {

    }

    @Nonnull
    @Override
    public Collection<Listener> getListeners()
    {
        return null;
    }

    public void setProjectManager( ProjectManager projectManager )
    {
        this.projectManager = projectManager;
    }

    public void setSystemProperties( Properties systemProperties )
    {
        this.systemProperties = systemProperties;
    }
}
