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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Organization;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.StringUtils;
import org.apache.maven.shared.utils.io.FileUtils;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.jar.ManifestException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

public class MavenArchiverTest
{
    static class ArtifactComparator
        implements Comparator<Artifact>
    {
        public int compare( Artifact o1, Artifact o2 )
        {
            return o1.getArtifactId().compareTo( o2.getArtifactId() );
        }

        public boolean equals( Object o )
        {
            return false;
        }
    }

    @Test
    public void testInvalidModuleNames()
    {
        assertThat( MavenArchiver.isValidModuleName( "" ) ).isFalse();
        assertThat( MavenArchiver.isValidModuleName( "." ) ).isFalse();
        assertThat( MavenArchiver.isValidModuleName( "dash-is-invalid" ) ).isFalse();
        assertThat( MavenArchiver.isValidModuleName( "plus+is+invalid" ) ).isFalse();
        assertThat( MavenArchiver.isValidModuleName( "colon:is:invalid" ) ).isFalse();
        assertThat( MavenArchiver.isValidModuleName( "new.class" ) ).isFalse();
        assertThat( MavenArchiver.isValidModuleName( "123.at.start.is.invalid" ) ).isFalse();
        assertThat( MavenArchiver.isValidModuleName( "digit.at.123start.is.invalid" ) ).isFalse();
    }

    @Test
    public void testValidModuleNames()
    {
        assertThat( MavenArchiver.isValidModuleName( "a" ) ).isTrue();
        assertThat( MavenArchiver.isValidModuleName( "a.b" ) ).isTrue();
        assertThat( MavenArchiver.isValidModuleName( "a_b" ) ).isTrue();
        assertThat( MavenArchiver.isValidModuleName( "trailing0.digits123.are456.ok789" ) ).isTrue();
        assertThat( MavenArchiver.isValidModuleName( "UTF8.chars.are.okay.äëïöüẍ" ) ).isTrue();
        assertThat( MavenArchiver.isValidModuleName( "ℤ€ℕ" ) ).isTrue();
    }

    @Test
    public void testGetManifestExtensionList()
        throws Exception
    {
        MavenArchiver archiver = new MavenArchiver();

        MavenSession session = getDummySession();

        Model model = new Model();
        model.setArtifactId( "dummy" );

        MavenProject project = new MavenProject( model );
        // we need to sort the artifacts for test purposes
        Set<Artifact> artifacts = new TreeSet<>( new ArtifactComparator() );
        project.setArtifacts( artifacts );

        // there should be a mock or a setter for this field.
        ManifestConfiguration config = new ManifestConfiguration()
        {
            public boolean isAddExtensions()
            {
                return true;
            }
        };

        Manifest manifest;

        manifest = archiver.getManifest( session, project, config );

        assertThat( manifest.getMainAttributes() ).isNotNull();

        assertThat( manifest.getMainAttributes().getValue( "Extension-List" ) ).isNull();

        MockArtifact artifact1 = new MockArtifact();
        artifact1.setGroupId( "org.apache.dummy" );
        artifact1.setArtifactId( "dummy1" );
        artifact1.setVersion( "1.0" );
        artifact1.setType( "dll" );
        artifact1.setScope( "compile" );

        artifacts.add( artifact1 );

        manifest = archiver.getManifest( session, project, config );

        assertThat( manifest.getMainAttributes().getValue( "Extension-List" ) ).isNull();

        MockArtifact artifact2 = new MockArtifact();
        artifact2.setGroupId( "org.apache.dummy" );
        artifact2.setArtifactId( "dummy2" );
        artifact2.setVersion( "1.0" );
        artifact2.setType( "jar" );
        artifact2.setScope( "compile" );

        artifacts.add( artifact2 );

        manifest = archiver.getManifest( session, project, config );

        assertThat( manifest.getMainAttributes().getValue( "Extension-List" ) ).isEqualTo( "dummy2" );

        MockArtifact artifact3 = new MockArtifact();
        artifact3.setGroupId( "org.apache.dummy" );
        artifact3.setArtifactId( "dummy3" );
        artifact3.setVersion( "1.0" );
        artifact3.setScope( "test" );
        artifact3.setType( "jar" );

        artifacts.add( artifact3 );

        manifest = archiver.getManifest( session, project, config );

        assertThat( manifest.getMainAttributes().getValue( "Extension-List" ) ).isEqualTo( "dummy2" );

        MockArtifact artifact4 = new MockArtifact();
        artifact4.setGroupId( "org.apache.dummy" );
        artifact4.setArtifactId( "dummy4" );
        artifact4.setVersion( "1.0" );
        artifact4.setType( "jar" );
        artifact4.setScope( "compile" );

        artifacts.add( artifact4 );

        manifest = archiver.getManifest( session, project, config );

        assertThat( manifest.getMainAttributes().getValue( "Extension-List" ) ).isEqualTo( "dummy2 dummy4" );
    }

    @Test
    public void testMultiClassPath()
        throws Exception
    {
        final File tempFile = File.createTempFile( "maven-archiver-test-", ".jar" );

        try
        {
            MavenArchiver archiver = new MavenArchiver();

            MavenSession session = getDummySession();

            Model model = new Model();
            model.setArtifactId( "dummy" );

            MavenProject project = new MavenProject( model )
            {
                public List<String> getRuntimeClasspathElements()
                {
                    return Collections.singletonList( tempFile.getAbsolutePath() );
                }
            };

            // there should be a mock or a setter for this field.
            ManifestConfiguration manifestConfig = new ManifestConfiguration()
            {
                public boolean isAddClasspath()
                {
                    return true;
                }
            };

            MavenArchiveConfiguration archiveConfiguration = new MavenArchiveConfiguration();
            archiveConfiguration.setManifest( manifestConfig );
            archiveConfiguration.addManifestEntry( "Class-Path", "help/" );

            Manifest manifest = archiver.getManifest( session, project, archiveConfiguration );
            String classPath = manifest.getMainAttributes().getValue( "Class-Path" );
            assertThat( classPath )
                    .as( "User specified Class-Path entry was not prepended to manifest" )
                    .startsWith( "help/" )
                    .as( "Class-Path generated by addClasspath was not appended to manifest" )
                    .endsWith( tempFile.getName() );
        }
        finally
        {
            // noinspection ResultOfMethodCallIgnored
            tempFile.delete();
        }
    }

    @Test
    @Ignore("Currently not working on Linux JDK 9 which looks like a JDK bug at the moment. See comments in Tests")
    public void testRecreation()
        throws Exception
    {
        File jarFile = new File( "target/test/dummy.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenSession session = getDummySession();
        MavenProject project = getDummyProject();

        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( false );

        FileUtils.deleteDirectory( "target/maven-archiver" );
        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();

        long history = System.currentTimeMillis() - 60000L;
        System.out.println( "history: " + history );
        jarFile.setLastModified( history );
        long time = jarFile.lastModified();
        System.out.println( "Time:" + time );

        List<File> files = FileUtils.getFiles( new File( "target/maven-archiver" ), "**/**", null, true );
        for ( File file : files )
        {
            assertThat( file.setLastModified( time ) ).isTrue();
        }

        archiver.createArchive( session, project, config );
        // Is the assumption correct that the jar file itself
        // should have the same last modified time as the files itself ?

        // Based on some experiments with a result like this:
        // History 1532810292406 time:1532810292000 expected:<1532810352000> but was:<1532810292000>
        // It looks like a JDK Bug: https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8177809
        assertThat( time ).as( "History " + history + " time:" + time ).isEqualTo( jarFile.lastModified() );

        config.setForced( true );
        archiver.createArchive( session, project, config );
        // I'm not sure if it could only be greater than time or if it is sufficient to be greater or equal..
        assertThat( jarFile.lastModified() ).isGreaterThanOrEqualTo( time );
    }

    @Test
    public void testNotGenerateImplementationVersionForMANIFESTMF()
        throws Exception
    {
        File jarFile = new File( "target/test/dummy.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenSession session = getDummySession();
        MavenProject project = getDummyProject();

        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( true );
        config.getManifest().setAddDefaultImplementationEntries( false );
        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();

        try ( JarFile jar = new JarFile( jarFile ) )
        {
            assertThat( jar.getManifest().getMainAttributes() )
                    .doesNotContainKey( Attributes.Name.IMPLEMENTATION_VERSION ); // "Implementation-Version"
        }
    }

    @Test
    public void testGenerateImplementationVersionForMANIFESTMF()
        throws Exception
    {
        File jarFile = new File( "target/test/dummy.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenSession session = getDummySession();
        MavenProject project = getDummyProject();

        String ls = System.getProperty( "line.separator" );
        project.setDescription( "foo " + ls + " bar " );
        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( true );
        config.getManifest().setAddDefaultImplementationEntries( true );
        config.addManifestEntry( "Description", project.getDescription() );
        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();

        try ( JarFile jar = new JarFile( jarFile ) )
        {
            assertThat( jar.getManifest().getMainAttributes() )
                    .containsKey( Attributes.Name.IMPLEMENTATION_VERSION )
                    .containsEntry( Attributes.Name.IMPLEMENTATION_VERSION, "0.1.1" );
        }
    }

    private MavenArchiver getMavenArchiver( JarArchiver jarArchiver )
    {
        MavenArchiver archiver = new MavenArchiver();
        archiver.setArchiver( jarArchiver );
        archiver.setOutputFile( jarArchiver.getDestFile() );
        return archiver;
    }

    @Test
    public void testDashesInClassPath_MSHARED_134()
        throws IOException, ManifestException, DependencyResolutionRequiredException
    {
        File jarFile = new File( "target/test/dummyWithDashes.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenSession session = getDummySession();
        MavenProject project = getDummyProject();

        Set<Artifact> artifacts =
            getArtifacts( getMockArtifact1(), getArtifactWithDot(), getMockArtifact2(), getMockArtifact3() );

        project.setArtifacts( artifacts );

        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( false );

        final ManifestConfiguration mftConfig = config.getManifest();
        mftConfig.setMainClass( "org.apache.maven.Foo" );
        mftConfig.setAddClasspath( true );
        mftConfig.setAddExtensions( true );
        mftConfig.setClasspathPrefix( "./lib/" );

        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();
    }

    @Test
    public void testDashesInClassPath_MSHARED_182()
        throws IOException, ManifestException, DependencyResolutionRequiredException
    {
        File jarFile = new File( "target/test/dummy.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );
        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenSession session = getDummySession();
        MavenProject project = getDummyProject();

        Set<Artifact> artifacts =
            getArtifacts( getMockArtifact1(), getArtifactWithDot(), getMockArtifact2(), getMockArtifact3() );

        project.setArtifacts( artifacts );

        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( false );

        final ManifestConfiguration mftConfig = config.getManifest();
        mftConfig.setMainClass( "org.apache.maven.Foo" );
        mftConfig.setAddClasspath( true );
        mftConfig.setAddExtensions( true );
        mftConfig.setClasspathPrefix( "./lib/" );
        config.addManifestEntry( "Key1", "value1" );
        config.addManifestEntry( "key2", "value2" );

        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();
        final Attributes mainAttributes = getJarFileManifest( jarFile ).getMainAttributes();
        assertThat( mainAttributes.getValue( "Key1" ) ).isEqualTo( "value1" );
        assertThat( mainAttributes.getValue( "Key2" ) ).isEqualTo( "value2" );
    }

    @Test
    public void testCarriageReturnInManifestEntry()
        throws Exception
    {
        File jarFile = new File( "target/test/dummy.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenSession session = getDummySession();
        MavenProject project = getDummyProject();

        String ls = System.getProperty( "line.separator" );
        project.setDescription( "foo " + ls + " bar " );
        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( true );
        config.getManifest().setAddDefaultImplementationEntries( true );
        config.addManifestEntry( "Description", project.getDescription() );
        // config.addManifestEntry( "EntryWithTab", " foo tab " + ( '\u0009' ) + ( '\u0009' ) // + " bar tab" + ( //
        // '\u0009' // ) );
        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();

        final Manifest manifest = getJarFileManifest( jarFile );
        Attributes attributes = manifest.getMainAttributes();
        assertThat( project.getDescription().indexOf( ls ) ).isGreaterThan( 0 );
        Attributes.Name description = new Attributes.Name( "Description" );
        String value = attributes.getValue( description );
        assertThat( value ).isNotNull();
        assertThat( value.indexOf( ls ) ).isLessThanOrEqualTo( 0 );
    }

    @Test
    public void testDeprecatedCreateArchiveAPI()
        throws Exception
    {
        File jarFile = new File( "target/test/dummy.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenProject project = getDummyProject();
        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( true );
        config.getManifest().setAddDefaultImplementationEntries( true );
        config.getManifest().setAddDefaultSpecificationEntries( true );

        MavenSession session = getDummySessionWithoutMavenVersion();
        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();
        Attributes manifest = getJarFileManifest( jarFile ).getMainAttributes();

        // no version number
        assertThat( manifest )
                .containsEntry( new Attributes.Name( "Created-By" ), "Maven Archiver" )
                .containsEntry( Attributes.Name.SPECIFICATION_TITLE, "archiver test" )
                .containsEntry( Attributes.Name.SPECIFICATION_VERSION, "0.1" )
                .containsEntry( Attributes.Name.SPECIFICATION_VENDOR, "Apache" )
                .containsEntry( Attributes.Name.IMPLEMENTATION_TITLE, "archiver test" )
                .containsEntry( Attributes.Name.IMPLEMENTATION_VERSION, "0.1.1" )
                .containsEntry( Attributes.Name.IMPLEMENTATION_VENDOR, "Apache" )
                .containsEntry( new Attributes.Name( "Build-Jdk-Spec" ),
                        System.getProperty( "java.specification.version" ) );
    }

    @Test
    public void testMinimalManifestEntries()
        throws Exception
    {
        File jarFile = new File( "target/test/dummy.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenSession session = getDummySession();
        MavenProject project = getDummyProject();
        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( true );
        config.getManifest().setAddDefaultEntries( false );

        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();

        final Manifest jarFileManifest = getJarFileManifest( jarFile );
        Attributes manifest = jarFileManifest.getMainAttributes();

        assertThat( manifest ).hasSize( 1 ).containsOnlyKeys( new Attributes.Name( "Manifest-Version" ) );
        assertThat( manifest.getValue( "Manifest-Version" ) ).isEqualTo( "1.0" );
    }


    @Test
    public void testManifestEntries()
        throws Exception
    {
        File jarFile = new File( "target/test/dummy.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenSession session = getDummySession();
        MavenProject project = getDummyProject();
        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( true );
        config.getManifest().setAddDefaultImplementationEntries( true );
        config.getManifest().setAddDefaultSpecificationEntries( true );
        config.getManifest().setAddBuildEnvironmentEntries( true );

        Map<String, String> manifestEntries = new HashMap<>();
        manifestEntries.put( "foo", "bar" );
        manifestEntries.put( "first-name", "olivier" );
        manifestEntries.put( "Automatic-Module-Name", "org.apache.maven.archiver" );
        manifestEntries.put( "keyWithEmptyValue", null );
        config.setManifestEntries( manifestEntries );

        ManifestSection manifestSection = new ManifestSection();
        manifestSection.setName( "UserSection" );
        manifestSection.addManifestEntry( "key", "value" );
        List<ManifestSection> manifestSections = new ArrayList<>();
        manifestSections.add( manifestSection );
        config.setManifestSections( manifestSections );
        config.getManifest().setMainClass( "org.apache.maven.Foo" );
        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();

        final Manifest jarFileManifest = getJarFileManifest( jarFile );
        Attributes manifest = jarFileManifest.getMainAttributes();

        // no version number
        assertThat( manifest )
                .containsEntry( new Attributes.Name( "Created-By" ), "Maven Archiver" )
                .containsEntry( new Attributes.Name( "Build-Tool" ),
                        session.getSystemProperties().get( "maven.build.version" ) )
                .containsEntry( new Attributes.Name( "Build-Jdk" ), String.format( "%s (%s)",
                        System.getProperty( "java.version" ), System.getProperty( "java.vendor" ) ) )
                .containsEntry( new Attributes.Name( "Build-Os" ), String.format( "%s (%s; %s)",
                        System.getProperty( "os.name" ), System.getProperty( "os.version" ),
                        System.getProperty( "os.arch" ) ) )
                .containsEntry( Attributes.Name.SPECIFICATION_TITLE, "archiver test" )
                .containsEntry( Attributes.Name.SPECIFICATION_VERSION, "0.1" )
                .containsEntry( Attributes.Name.SPECIFICATION_VENDOR, "Apache" )
                .containsEntry( Attributes.Name.IMPLEMENTATION_TITLE, "archiver test" )
                .containsEntry( Attributes.Name.IMPLEMENTATION_VERSION, "0.1.1" )
                .containsEntry( Attributes.Name.IMPLEMENTATION_VENDOR, "Apache" )
                .containsEntry( Attributes.Name.MAIN_CLASS, "org.apache.maven.Foo" )
                .containsEntry( new Attributes.Name( "foo" ), "bar" )
                .containsEntry( new Attributes.Name( "first-name" ), "olivier" );

        assertThat( manifest.getValue( "Automatic-Module-Name" ) ).isEqualTo( "org.apache.maven.archiver" );

        assertThat( manifest ).containsEntry( new Attributes.Name( "Build-Jdk-Spec" ),
                System.getProperty( "java.specification.version" ) );

        assertThat( StringUtils.isEmpty( manifest.getValue( new Attributes.Name( "keyWithEmptyValue" ) ) ) ).isTrue();
        assertThat( manifest ).containsKey( new Attributes.Name( "keyWithEmptyValue" ) );

        manifest = jarFileManifest.getAttributes( "UserSection" );

        assertThat( manifest ).containsEntry( new Attributes.Name( "key" ), "value" );
    }

    @Test
    public void testManifestWithInvalidAutomaticModuleNameThrowsOnCreateArchive()
            throws Exception
    {
        File jarFile = new File( "target/test/dummy.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenSession session = getDummySession();
        MavenProject project = getDummyProject();
        MavenArchiveConfiguration config = new MavenArchiveConfiguration();

        Map<String, String> manifestEntries = new HashMap<>();
        manifestEntries.put( "Automatic-Module-Name", "123.in-valid.new.name" );
        config.setManifestEntries( manifestEntries );

        try
        {
            archiver.createArchive( session, project, config );
        }
        catch ( ManifestException e )
        {
            assertThat( e.getMessage() ).isEqualTo( "Invalid automatic module name: '123.in-valid.new.name'" );
        }
    }

    /*
     * Test to make sure that manifest sections are present in the manifest prior to the archive has been created.
     */
    @Test
    public void testManifestSections()
        throws Exception
    {
        MavenArchiver archiver = new MavenArchiver();

        MavenSession session = getDummySession();

        MavenProject project = getDummyProject();
        MavenArchiveConfiguration config = new MavenArchiveConfiguration();

        ManifestSection manifestSection = new ManifestSection();
        manifestSection.setName( "SectionOne" );
        manifestSection.addManifestEntry( "key", "value" );
        List<ManifestSection> manifestSections = new ArrayList<>();
        manifestSections.add( manifestSection );
        config.setManifestSections( manifestSections );

        Manifest manifest = archiver.getManifest( session, project, config );

        Attributes section = manifest.getAttributes( "SectionOne" );
        assertThat( section ).as( "The section is not present in the manifest as it should be." ).isNotNull();

        String attribute = section.getValue( "key" );
        assertThat( attribute )
                .as( "The attribute we are looking for is not present in the section." ).isNotNull()
                .as( "The value of the attribute is wrong." ).isEqualTo( "value" );
    }

    @Test
    public void testDefaultClassPathValue()
        throws Exception
    {
        MavenSession session = getDummySession();
        MavenProject project = getDummyProject();
        File jarFile = new File( "target/test/dummy.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( true );
        config.getManifest().setAddDefaultImplementationEntries( true );
        config.getManifest().setAddDefaultSpecificationEntries( true );
        config.getManifest().setMainClass( "org.apache.maven.Foo" );
        config.getManifest().setAddClasspath( true );
        config.getManifest().setClasspathLayoutType( ManifestConfiguration.CLASSPATH_LAYOUT_TYPE_CUSTOM );
        config.getManifest().setCustomClasspathLayout( "${artifact.artifactId}-${artifact.version}${dashClassifier?}.${artifact.extension}" );
        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();
        final Manifest manifest = getJarFileManifest( jarFile );
        String classPath = manifest.getMainAttributes().getValue( Attributes.Name.CLASS_PATH );
        assertThat( classPath ).isNotNull();
        assertThat( StringUtils.split( classPath, " " ) )
                .containsExactly( "dummy1-1.0.jar", "dummy2-1.5.jar", "dummy3-2.0.jar" );
    }

    private void deleteAndAssertNotPresent( File jarFile )
    {
        jarFile.delete();
        assertThat( jarFile ).doesNotExist();
    }

    @Test
    public void testDefaultClassPathValue_WithSnapshot()
        throws Exception
    {
        MavenSession session = getDummySession();
        MavenProject project = getDummyProjectWithSnapshot();
        File jarFile = new File( "target/test/dummy.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( true );
        config.getManifest().setAddDefaultImplementationEntries( true );
        config.getManifest().setAddDefaultSpecificationEntries( true );
        config.getManifest().setMainClass( "org.apache.maven.Foo" );
        config.getManifest().setAddClasspath( true );
        config.getManifest().setClasspathLayoutType( ManifestConfiguration.CLASSPATH_LAYOUT_TYPE_CUSTOM );
        config.getManifest().setCustomClasspathLayout( "${artifact.artifactId}-${artifact.version}${dashClassifier?}.${artifact.extension}" );
        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();

        final Manifest manifest = getJarFileManifest( jarFile );
        String classPath = manifest.getMainAttributes().getValue( Attributes.Name.CLASS_PATH );
        assertThat( classPath ).isNotNull();
        assertThat( StringUtils.split( classPath, " " ) )
                .containsExactly( "dummy1-1.1-20081022.112233-1.jar", "dummy2-1.5.jar", "dummy3-2.0.jar" );
    }

    @Test
    public void testMavenRepoClassPathValue()
        throws Exception
    {
        MavenSession session = getDummySession();
        MavenProject project = getDummyProject();
        File jarFile = new File( "target/test/dummy.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( true );
        config.getManifest().setAddDefaultImplementationEntries( true );
        config.getManifest().setAddDefaultSpecificationEntries( true );
        config.getManifest().setMainClass( "org.apache.maven.Foo" );
        config.getManifest().setAddClasspath( true );
        config.getManifest().setUseUniqueVersions( true );
        config.getManifest().setClasspathLayoutType( ManifestConfiguration.CLASSPATH_LAYOUT_TYPE_REPOSITORY );
        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();
        Manifest manifest = archiver.getManifest( session, project, config );
        String[] classPathEntries =
            StringUtils.split( new String( manifest.getMainAttributes().getValue( "Class-Path" ).getBytes() ), " " );
        assertThat( classPathEntries ).containsExactly(
                "org/apache/dummy/dummy1/1.0.1/dummy1-1.0.jar",
                "org/apache/dummy/foo/dummy2/1.5/dummy2-1.5.jar",
                "org/apache/dummy/bar/dummy3/2.0/dummy3-2.0.jar" );

        String classPath = getJarFileManifest( jarFile ).getMainAttributes().getValue( Attributes.Name.CLASS_PATH );
        assertThat( classPath ).isNotNull();
        assertThat( StringUtils.split( classPath, " " ) ).containsExactly(
                "org/apache/dummy/dummy1/1.0.1/dummy1-1.0.jar",
                "org/apache/dummy/foo/dummy2/1.5/dummy2-1.5.jar",
                "org/apache/dummy/bar/dummy3/2.0/dummy3-2.0.jar" );
    }

    @Test
    public void shouldCreateArchiveWithSimpleClassPathLayoutWhileSettingSimpleLayoutExplicit()
        throws Exception
    {
        MavenSession session = getDummySession();
        MavenProject project = getDummyProject();
        File jarFile = new File( "target/test/dummy-explicit-simple.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( true );
        config.getManifest().setAddDefaultImplementationEntries( true );
        config.getManifest().setAddDefaultSpecificationEntries( true );
        config.getManifest().setMainClass( "org.apache.maven.Foo" );
        config.getManifest().setAddClasspath( true );
        config.getManifest().setClasspathPrefix( "lib" );
        config.getManifest().setClasspathLayoutType( ManifestConfiguration.CLASSPATH_LAYOUT_TYPE_SIMPLE );

        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();
        Manifest manifest = archiver.getManifest( session, project, config );
        String[] classPathEntries =
            StringUtils.split( new String( manifest.getMainAttributes().getValue( "Class-Path" ).getBytes() ), " " );
        assertThat( classPathEntries )
                .containsExactly( "lib/dummy1-1.0.jar", "lib/dummy2-1.5.jar", "lib/dummy3-2.0.jar" );

        String classPath = getJarFileManifest( jarFile ).getMainAttributes().getValue( Attributes.Name.CLASS_PATH );

        assertThat( classPath ).isNotNull();
        assertThat( StringUtils.split( classPath, " " ) )
                .containsExactly( "lib/dummy1-1.0.jar", "lib/dummy2-1.5.jar", "lib/dummy3-2.0.jar" );
    }

    @Test
    public void shouldCreateArchiveCustomerLayoutSimple()
        throws Exception
    {
        MavenSession session = getDummySession();
        MavenProject project = getDummyProject();
        File jarFile = new File( "target/test/dummy-custom-layout-simple.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( true );
        config.getManifest().setAddDefaultImplementationEntries( true );
        config.getManifest().setAddDefaultSpecificationEntries( true );
        config.getManifest().setMainClass( "org.apache.maven.Foo" );
        config.getManifest().setAddClasspath( true );
        config.getManifest().setClasspathPrefix( "lib" );
        config.getManifest().setClasspathLayoutType( ManifestConfiguration.CLASSPATH_LAYOUT_TYPE_CUSTOM );
        config.getManifest().setCustomClasspathLayout( MavenArchiver.SIMPLE_LAYOUT );

        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();
        Manifest manifest = archiver.getManifest( session, project, config );
        String[] classPathEntries =
            StringUtils.split( new String( manifest.getMainAttributes().getValue( "Class-Path" ).getBytes() ), " " );
        assertThat( classPathEntries )
                .containsExactly( "lib/dummy1-1.0.jar", "lib/dummy2-1.5.jar", "lib/dummy3-2.0.jar" );

        String classPath = getJarFileManifest( jarFile ).getMainAttributes().getValue( Attributes.Name.CLASS_PATH );

        assertThat( classPath ).isNotNull();
        assertThat( StringUtils.split( classPath, " " ) )
                .containsExactly( "lib/dummy1-1.0.jar", "lib/dummy2-1.5.jar", "lib/dummy3-2.0.jar" );
    }

    @Test
    public void shouldCreateArchiveCustomLayoutSimpleNonUnique()
        throws Exception
    {
        MavenSession session = getDummySession();
        MavenProject project = getDummyProject();
        File jarFile = new File( "target/test/dummy-custom-layout-simple-non-unique.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( true );
        config.getManifest().setAddDefaultImplementationEntries( true );
        config.getManifest().setAddDefaultSpecificationEntries( true );
        config.getManifest().setMainClass( "org.apache.maven.Foo" );
        config.getManifest().setAddClasspath( true );
        config.getManifest().setClasspathPrefix( "lib" );
        config.getManifest().setClasspathLayoutType( ManifestConfiguration.CLASSPATH_LAYOUT_TYPE_CUSTOM );
        config.getManifest().setCustomClasspathLayout( MavenArchiver.SIMPLE_LAYOUT_NONUNIQUE );

        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();
        Manifest manifest = archiver.getManifest( session, project, config );
        String[] classPathEntries =
            StringUtils.split( new String( manifest.getMainAttributes().getValue( "Class-Path" ).getBytes() ), " " );
        assertThat( classPathEntries )
                .containsExactly( "lib/dummy1-1.0.1.jar", "lib/dummy2-1.5.jar", "lib/dummy3-2.0.jar" );

        String classPath = getJarFileManifest( jarFile ).getMainAttributes().getValue( Attributes.Name.CLASS_PATH );

        assertThat( classPath ).isNotNull();
        assertThat( StringUtils.split( classPath, " " ) )
                .containsExactly( "lib/dummy1-1.0.1.jar", "lib/dummy2-1.5.jar", "lib/dummy3-2.0.jar" );
    }

    @Test
    public void shouldCreateArchiveCustomLayoutRepository()
        throws Exception
    {
        MavenSession session = getDummySession();
        MavenProject project = getDummyProject();
        File jarFile = new File( "target/test/dummy-custom-layout-repo.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( true );
        config.getManifest().setAddDefaultImplementationEntries( true );
        config.getManifest().setAddDefaultSpecificationEntries( true );
        config.getManifest().setMainClass( "org.apache.maven.Foo" );
        config.getManifest().setAddClasspath( true );
        config.getManifest().setClasspathPrefix( "lib" );
        config.getManifest().setClasspathLayoutType( ManifestConfiguration.CLASSPATH_LAYOUT_TYPE_CUSTOM );
        config.getManifest().setCustomClasspathLayout( MavenArchiver.REPOSITORY_LAYOUT );

        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();
        Manifest manifest = archiver.getManifest( session, project, config );
        String[] classPathEntries =
            StringUtils.split( new String( manifest.getMainAttributes().getValue( "Class-Path" ).getBytes() ), " " );
        assertThat( classPathEntries ).containsExactly(
                "lib/org/apache/dummy/dummy1/1.0.1/dummy1-1.0.jar",
                "lib/org/apache/dummy/foo/dummy2/1.5/dummy2-1.5.jar",
                "lib/org/apache/dummy/bar/dummy3/2.0/dummy3-2.0.jar" );

        String classPath = getJarFileManifest( jarFile ).getMainAttributes().getValue( Attributes.Name.CLASS_PATH );

        assertThat( classPath ).isNotNull();
        assertThat( StringUtils.split( classPath, " " ) ).containsExactly(
                "lib/org/apache/dummy/dummy1/1.0.1/dummy1-1.0.jar",
                "lib/org/apache/dummy/foo/dummy2/1.5/dummy2-1.5.jar",
                "lib/org/apache/dummy/bar/dummy3/2.0/dummy3-2.0.jar" );
    }

    @Test
    public void shouldCreateArchiveCustomLayoutRepositoryNonUnique()
        throws Exception
    {
        MavenSession session = getDummySession();
        MavenProject project = getDummyProject();
        File jarFile = new File( "target/test/dummy-custom-layout-repo-non-unique.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( true );
        config.getManifest().setAddDefaultImplementationEntries( true );
        config.getManifest().setAddDefaultSpecificationEntries( true );
        config.getManifest().setMainClass( "org.apache.maven.Foo" );
        config.getManifest().setAddClasspath( true );
        config.getManifest().setClasspathPrefix( "lib" );
        config.getManifest().setClasspathLayoutType( ManifestConfiguration.CLASSPATH_LAYOUT_TYPE_CUSTOM );
        config.getManifest().setCustomClasspathLayout( MavenArchiver.REPOSITORY_LAYOUT_NONUNIQUE );

        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();
        Manifest manifest = archiver.getManifest( session, project, config );
        String[] classPathEntries =
            StringUtils.split( new String( manifest.getMainAttributes().getValue( "Class-Path" ).getBytes() ), " " );
        assertThat( classPathEntries ).containsExactly(
                "lib/org/apache/dummy/dummy1/1.0.1/dummy1-1.0.1.jar",
                "lib/org/apache/dummy/foo/dummy2/1.5/dummy2-1.5.jar",
                "lib/org/apache/dummy/bar/dummy3/2.0/dummy3-2.0.jar" );

        String classPath = getJarFileManifest( jarFile ).getMainAttributes().getValue( Attributes.Name.CLASS_PATH );

        assertThat( classPath ).isNotNull();
        assertThat( StringUtils.split( classPath, " " ) ).containsExactly(
                "lib/org/apache/dummy/dummy1/1.0.1/dummy1-1.0.1.jar",
                "lib/org/apache/dummy/foo/dummy2/1.5/dummy2-1.5.jar",
                "lib/org/apache/dummy/bar/dummy3/2.0/dummy3-2.0.jar" );
    }

    @Test
    public void shouldCreateArchiveWithSimpleClassPathLayoutUsingDefaults()
        throws Exception
    {
        MavenSession session = getDummySession();
        MavenProject project = getDummyProject();
        File jarFile = new File( "target/test/dummy-defaults.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( true );
        config.getManifest().setAddDefaultImplementationEntries( true );
        config.getManifest().setAddDefaultSpecificationEntries( true );
        config.getManifest().setMainClass( "org.apache.maven.Foo" );
        config.getManifest().setAddClasspath( true );
        config.getManifest().setClasspathPrefix( "lib" );

        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();
        Manifest manifest = archiver.getManifest( session, project, config );
        String[] classPathEntries =
            StringUtils.split( new String( manifest.getMainAttributes().getValue( "Class-Path" ).getBytes() ), " " );
        assertThat( classPathEntries )
                .containsExactly( "lib/dummy1-1.0.jar", "lib/dummy2-1.5.jar", "lib/dummy3-2.0.jar" );

        String classPath = getJarFileManifest( jarFile ).getMainAttributes().getValue( Attributes.Name.CLASS_PATH );
        assertThat( classPath ).isNotNull();
        assertThat( StringUtils.split( classPath, " " ) )
                .containsExactly( "lib/dummy1-1.0.jar", "lib/dummy2-1.5.jar", "lib/dummy3-2.0.jar" );
    }

    @Test
    public void testMavenRepoClassPathValue_WithSnapshot()
        throws Exception
    {
        MavenSession session = getDummySession();
        MavenProject project = getDummyProjectWithSnapshot();
        File jarFile = new File( "target/test/dummy.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( true );
        config.getManifest().setAddDefaultImplementationEntries( true );
        config.getManifest().setAddDefaultSpecificationEntries( true );
        config.getManifest().setMainClass( "org.apache.maven.Foo" );
        config.getManifest().setAddClasspath( true );
        config.getManifest().setClasspathLayoutType( ManifestConfiguration.CLASSPATH_LAYOUT_TYPE_REPOSITORY );
        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();

        Manifest manifest = archiver.getManifest( session, project, config );
        String[] classPathEntries =
            StringUtils.split( new String( manifest.getMainAttributes().getValue( "Class-Path" ).getBytes() ), " " );
        assertThat( classPathEntries ).containsExactly(
                "org/apache/dummy/dummy1/1.1-SNAPSHOT/dummy1-1.1-20081022.112233-1.jar",
                "org/apache/dummy/foo/dummy2/1.5/dummy2-1.5.jar",
                "org/apache/dummy/bar/dummy3/2.0/dummy3-2.0.jar" );

        String classPath = getJarFileManifest( jarFile ).getMainAttributes().getValue( Attributes.Name.CLASS_PATH );
        assertThat( classPath ).isNotNull();
        assertThat( StringUtils.split( classPath, " " ) ).containsExactly(
                "org/apache/dummy/dummy1/1.1-SNAPSHOT/dummy1-1.1-20081022.112233-1.jar",
                "org/apache/dummy/foo/dummy2/1.5/dummy2-1.5.jar",
                "org/apache/dummy/bar/dummy3/2.0/dummy3-2.0.jar" );
    }

    @Test
    public void testCustomClassPathValue()
        throws Exception
    {
        MavenSession session = getDummySession();
        MavenProject project = getDummyProject();
        File jarFile = new File( "target/test/dummy.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( true );
        config.getManifest().setAddDefaultImplementationEntries( true );
        config.getManifest().setAddDefaultSpecificationEntries( true );
        config.getManifest().setMainClass( "org.apache.maven.Foo" );
        config.getManifest().setAddClasspath( true );
        config.getManifest().setClasspathLayoutType( ManifestConfiguration.CLASSPATH_LAYOUT_TYPE_CUSTOM );
        config.getManifest().setCustomClasspathLayout( "${artifact.groupIdPath}/${artifact.artifactId}/${artifact.version}/TEST-${artifact.artifactId}-${artifact.version}${dashClassifier?}.${artifact.extension}" );
        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();
        Manifest manifest = archiver.getManifest( session, project, config );
        String[] classPathEntries =
            StringUtils.split( new String( manifest.getMainAttributes().getValue( "Class-Path" ).getBytes() ), " " );
        assertThat( classPathEntries ).containsExactly(
                "org/apache/dummy/dummy1/1.0/TEST-dummy1-1.0.jar",
                "org/apache/dummy/foo/dummy2/1.5/TEST-dummy2-1.5.jar",
                "org/apache/dummy/bar/dummy3/2.0/TEST-dummy3-2.0.jar" );

        final Manifest manifest1 = getJarFileManifest( jarFile );
        String classPath = manifest1.getMainAttributes().getValue( Attributes.Name.CLASS_PATH );
        assertThat( classPath ).isNotNull();
        assertThat( StringUtils.split( classPath, " " ) ).containsExactly(
                "org/apache/dummy/dummy1/1.0/TEST-dummy1-1.0.jar",
                "org/apache/dummy/foo/dummy2/1.5/TEST-dummy2-1.5.jar",
                "org/apache/dummy/bar/dummy3/2.0/TEST-dummy3-2.0.jar" );
    }

    @Test
    public void testCustomClassPathValue_WithSnapshotResolvedVersion()
        throws Exception
    {
        MavenSession session = getDummySession();
        MavenProject project = getDummyProjectWithSnapshot();
        File jarFile = new File( "target/test/dummy.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );
        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( true );
        config.getManifest().setAddDefaultImplementationEntries( true );
        config.getManifest().setAddDefaultSpecificationEntries( true );
        config.getManifest().setMainClass( "org.apache.maven.Foo" );
        config.getManifest().setAddClasspath( true );
        config.getManifest().setClasspathLayoutType( ManifestConfiguration.CLASSPATH_LAYOUT_TYPE_CUSTOM );
        config.getManifest().setCustomClasspathLayout( "${artifact.groupIdPath}/${artifact.artifactId}/${artifact.baseVersion}/TEST-${artifact.artifactId}-${artifact.version}${dashClassifier?}.${artifact.extension}" );
        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();

        Manifest manifest = archiver.getManifest( session, project, config );
        String[] classPathEntries =
            StringUtils.split( new String( manifest.getMainAttributes().getValue( "Class-Path" ).getBytes() ), " " );
        assertThat( classPathEntries ).containsExactly(
                "org/apache/dummy/dummy1/1.1-SNAPSHOT/TEST-dummy1-1.1-20081022.112233-1.jar",
                "org/apache/dummy/foo/dummy2/1.5/TEST-dummy2-1.5.jar",
                "org/apache/dummy/bar/dummy3/2.0/TEST-dummy3-2.0.jar" );

        String classPath = getJarFileManifest( jarFile ).getMainAttributes().getValue( Attributes.Name.CLASS_PATH );
        assertThat( classPath ).isNotNull();
        assertThat( StringUtils.split( classPath, " " ) ).containsExactly(
                "org/apache/dummy/dummy1/1.1-SNAPSHOT/TEST-dummy1-1.1-20081022.112233-1.jar",
                "org/apache/dummy/foo/dummy2/1.5/TEST-dummy2-1.5.jar",
                "org/apache/dummy/bar/dummy3/2.0/TEST-dummy3-2.0.jar" );
    }

    @Test
    public void testCustomClassPathValue_WithSnapshotForcingBaseVersion()
        throws Exception
    {
        MavenSession session = getDummySession();
        MavenProject project = getDummyProjectWithSnapshot();
        File jarFile = new File( "target/test/dummy.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( true );
        config.getManifest().setAddDefaultImplementationEntries( true );
        config.getManifest().setAddDefaultSpecificationEntries( true );
        config.getManifest().setMainClass( "org.apache.maven.Foo" );
        config.getManifest().setAddClasspath( true );
        config.getManifest().setClasspathLayoutType( ManifestConfiguration.CLASSPATH_LAYOUT_TYPE_CUSTOM );
        config.getManifest().setCustomClasspathLayout( "${artifact.groupIdPath}/${artifact.artifactId}/${artifact.baseVersion}/TEST-${artifact.artifactId}-${artifact.baseVersion}${dashClassifier?}.${artifact.extension}" );
        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();
        Manifest manifest = archiver.getManifest( session, project, config );
        String[] classPathEntries =
            StringUtils.split( new String( manifest.getMainAttributes().getValue( "Class-Path" ).getBytes() ), " " );
        assertThat( classPathEntries[0] ).isEqualTo(
                "org/apache/dummy/dummy1/1.1-SNAPSHOT/TEST-dummy1-1.1-SNAPSHOT.jar" );
        assertThat( classPathEntries[1] ).isEqualTo( "org/apache/dummy/foo/dummy2/1.5/TEST-dummy2-1.5.jar" );
        assertThat( classPathEntries[2] ).isEqualTo( "org/apache/dummy/bar/dummy3/2.0/TEST-dummy3-2.0.jar" );

        String classPath = getJarFileManifest( jarFile ).getMainAttributes().getValue( Attributes.Name.CLASS_PATH );
        assertThat( classPath ).isNotNull();
        assertThat( StringUtils.split( classPath, " " ) ).containsExactly(
                "org/apache/dummy/dummy1/1.1-SNAPSHOT/TEST-dummy1-1.1-SNAPSHOT.jar",
                "org/apache/dummy/foo/dummy2/1.5/TEST-dummy2-1.5.jar",
                "org/apache/dummy/bar/dummy3/2.0/TEST-dummy3-2.0.jar" );
    }

    @Test
    public void testDefaultPomProperties()
        throws Exception
    {
        MavenSession session = getDummySession();
        MavenProject project = getDummyProject();
        File jarFile = new File( "target/test/dummy.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( true );
        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();

        final String groupId = project.getGroupId();
        final String artifactId = project.getArtifactId();
        final String version = project.getVersion();

        JarFile virtJarFile = new JarFile( jarFile );
        ZipEntry pomPropertiesEntry =
            virtJarFile.getEntry( "META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties" );
        assertThat( pomPropertiesEntry ).isNotNull();

        try ( InputStream is = virtJarFile.getInputStream( pomPropertiesEntry ) )
        {
            Properties p = new Properties();
            p.load( is );

            assertThat( p.getProperty( "groupId" ) ).isEqualTo( groupId );
            assertThat( p.getProperty( "artifactId" ) ).isEqualTo( artifactId );
            assertThat( p.getProperty( "version" ) ).isEqualTo( version );
        }
        virtJarFile.close();
    }

    @Test
    public void testCustomPomProperties()
        throws Exception
    {
        MavenSession session = getDummySession();
        MavenProject project = getDummyProject();
        File jarFile = new File( "target/test/dummy.jar" );
        JarArchiver jarArchiver = getCleanJarArchiver( jarFile );

        MavenArchiver archiver = getMavenArchiver( jarArchiver );

        File customPomPropertiesFile = new File( "src/test/resources/custom-pom.properties" );
        MavenArchiveConfiguration config = new MavenArchiveConfiguration();
        config.setForced( true );
        config.setPomPropertiesFile( customPomPropertiesFile );
        archiver.createArchive( session, project, config );
        assertThat( jarFile ).exists();

        final String groupId = project.getGroupId();
        final String artifactId = project.getArtifactId();
        final String version = project.getVersion();

        try ( JarFile virtJarFile = new JarFile( jarFile ) )
        {
            ZipEntry pomPropertiesEntry = virtJarFile
                    .getEntry( "META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties" );
            assertThat( pomPropertiesEntry ).isNotNull();

            try ( InputStream is = virtJarFile.getInputStream( pomPropertiesEntry ) )
            {
                Properties p = new Properties();
                p.load( is );

                assertThat( p.getProperty( "groupId" ) ).isEqualTo( groupId );
                assertThat( p.getProperty( "artifactId" ) ).isEqualTo( artifactId );
                assertThat( p.getProperty( "version" ) ).isEqualTo( version );
                assertThat( p.getProperty( "build.revision" ) ).isEqualTo( "1337" );
                assertThat( p.getProperty( "build.branch" ) ).isEqualTo( "tags/0.1.1" );
            }
        }
    }

    private JarArchiver getCleanJarArchiver( File jarFile )
    {
        deleteAndAssertNotPresent( jarFile );
        JarArchiver jarArchiver = new JarArchiver();
        jarArchiver.setDestFile( jarFile );
        return jarArchiver;
    }

    // ----------------------------------------
    // common methods for testing
    // ----------------------------------------

    private MavenProject getDummyProject()
    {
        MavenProject project = getMavenProject();
        File pomFile = new File( "src/test/resources/pom.xml" );
        pomFile.setLastModified( System.currentTimeMillis() - 60000L );
        project.setFile( pomFile );
        Build build = new Build();
        build.setDirectory( "target" );
        build.setOutputDirectory( "target" );
        project.setBuild( build );
        project.setName( "archiver test" );
        project.setUrl( "https://maven.apache.org" );
        Organization organization = new Organization();
        organization.setName( "Apache" );
        project.setOrganization( organization );
        MockArtifact artifact = new MockArtifact();
        artifact.setGroupId( "org.apache.dummy" );
        artifact.setArtifactId( "dummy" );
        artifact.setVersion( "0.1.1" );
        artifact.setBaseVersion( "0.1.2" );
        artifact.setType( "jar" );
        artifact.setArtifactHandler( new DefaultArtifactHandler( "jar" ) );
        project.setArtifact( artifact );

        Set<Artifact> artifacts = getArtifacts( getMockArtifact1Release(), getMockArtifact2(), getMockArtifact3() );
        project.setArtifacts( artifacts );

        return project;
    }

    private MavenProject getMavenProject()
    {
        Model model = new Model();
        model.setGroupId( "org.apache.dummy" );
        model.setArtifactId( "dummy" );
        model.setVersion( "0.1.1" );

        final MavenProject project = new MavenProject( model );
        project.setExtensionArtifacts( Collections.<Artifact>emptySet() );
        project.setRemoteArtifactRepositories( Collections.<ArtifactRepository>emptyList() );
        project.setPluginArtifactRepositories( Collections.<ArtifactRepository>emptyList() );
        return project;
    }

    private MockArtifact getMockArtifact3()
    {
        MockArtifact artifact3 = new MockArtifact();
        artifact3.setGroupId( "org.apache.dummy.bar" );
        artifact3.setArtifactId( "dummy3" );
        artifact3.setVersion( "2.0" );
        artifact3.setScope( "runtime" );
        artifact3.setType( "jar" );
        artifact3.setFile( getClasspathFile( artifact3.getArtifactId() + "-" + artifact3.getVersion() + ".jar" ) );
        return artifact3;
    }

    private MavenProject getDummyProjectWithSnapshot()
    {
        MavenProject project = getMavenProject();
        File pomFile = new File( "src/test/resources/pom.xml" );
        pomFile.setLastModified( System.currentTimeMillis() - 60000L );
        project.setFile( pomFile );
        Build build = new Build();
        build.setDirectory( "target" );
        build.setOutputDirectory( "target" );
        project.setBuild( build );
        project.setName( "archiver test" );
        Organization organization = new Organization();
        organization.setName( "Apache" );
        project.setOrganization( organization );

        MockArtifact artifact = new MockArtifact();
        artifact.setGroupId( "org.apache.dummy" );
        artifact.setArtifactId( "dummy" );
        artifact.setVersion( "0.1.1" );
        artifact.setBaseVersion( "0.1.1" );
        artifact.setType( "jar" );
        artifact.setArtifactHandler( new DefaultArtifactHandler( "jar" ) );
        project.setArtifact( artifact );

        Set<Artifact> artifacts = getArtifacts( getMockArtifact1(), getMockArtifact2(), getMockArtifact3() );

        project.setArtifacts( artifacts );

        return project;
    }

    private ArtifactHandler getMockArtifactHandler()
    {
        return new ArtifactHandler()
        {

            public String getClassifier()
            {
                return null;
            }

            public String getDirectory()
            {
                return null;
            }

            public String getExtension()
            {
                return "jar";
            }

            public String getLanguage()
            {
                return null;
            }

            public String getPackaging()
            {
                return null;
            }

            public boolean isAddedToClasspath()
            {
                return true;
            }

            public boolean isIncludesDependencies()
            {
                return false;
            }

        };
    }

    private MockArtifact getMockArtifact2()
    {
        MockArtifact artifact2 = new MockArtifact();
        artifact2.setGroupId( "org.apache.dummy.foo" );
        artifact2.setArtifactId( "dummy2" );
        artifact2.setVersion( "1.5" );
        artifact2.setType( "jar" );
        artifact2.setScope( "runtime" );
        artifact2.setFile( getClasspathFile( artifact2.getArtifactId() + "-" + artifact2.getVersion() + ".jar" ) );
        return artifact2;
    }

    private MockArtifact getArtifactWithDot()
    {
        MockArtifact artifact2 = new MockArtifact();
        artifact2.setGroupId( "org.apache.dummy.foo" );
        artifact2.setArtifactId( "dummy.dot" );
        artifact2.setVersion( "1.5" );
        artifact2.setType( "jar" );
        artifact2.setScope( "runtime" );
        artifact2.setFile( getClasspathFile( artifact2.getArtifactId() + "-" + artifact2.getVersion() + ".jar" ) );
        return artifact2;
    }

    private MockArtifact getMockArtifact1()
    {
        MockArtifact artifact1 = new MockArtifact();
        artifact1.setGroupId( "org.apache.dummy" );
        artifact1.setArtifactId( "dummy1" );
        artifact1.setSnapshotVersion( "1.1-20081022.112233-1", "1.1-SNAPSHOT" );
        artifact1.setType( "jar" );
        artifact1.setScope( "runtime" );
        artifact1.setFile( getClasspathFile( artifact1.getArtifactId() + "-" + artifact1.getVersion() + ".jar" ) );
        return artifact1;
    }

    private MockArtifact getMockArtifact1Release()
    {
        MockArtifact artifact1 = new MockArtifact();
        artifact1.setGroupId( "org.apache.dummy" );
        artifact1.setArtifactId( "dummy1" );
        artifact1.setVersion( "1.0" );
        artifact1.setBaseVersion( "1.0.1" );
        artifact1.setType( "jar" );
        artifact1.setScope( "runtime" );
        artifact1.setFile( getClasspathFile( artifact1.getArtifactId() + "-" + artifact1.getVersion() + ".jar" ) );
        return artifact1;
    }

    private File getClasspathFile( String file )
    {
        URL resource = Thread.currentThread().getContextClassLoader().getResource( file );
        if ( resource == null )
        {
            fail( "Cannot retrieve java.net.URL for file: " + file + " on the current test classpath." );
        }

        URI uri = new File( resource.getPath() ).toURI().normalize();

        return new File( uri.getPath().replaceAll( "%20", " " ) );
    }

    private MavenSession getDummySession()
    {
        Properties systemProperties = new Properties();
        systemProperties.put( "maven.version", "3.0.4" );
        systemProperties.put( "maven.build.version",
            "Apache Maven 3.0.4 (3ad2b6794a8293a8ca6c1590708fb5d3fc795c49; 2012-01-17T08:39:41Z)" );

        return getDummySession( systemProperties );
    }

    private MavenSession getDummySessionWithoutMavenVersion()
    {
        return getDummySession( new Properties() );
    }

    private MavenSession getDummySession( Properties systemProperties )
    {
        PlexusContainer container = null;
        File settings = null;
        List<String> goals = null;
        Date startTime = new Date();

        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setSystemProperties( systemProperties );
        request.setGoals( goals );
        request.setStartTime( startTime );
        request.setUserSettingsFile( settings );

        MavenExecutionResult result = new DefaultMavenExecutionResult();

        RepositorySystemSession rss = new DefaultRepositorySystemSession();

        return new MavenSession( container, rss, request, result );

    }

    private Set<Artifact> getArtifacts( Artifact... artifacts )
    {
        final ArtifactHandler mockArtifactHandler = getMockArtifactHandler();
        Set<Artifact> result = new TreeSet<>( new ArtifactComparator() );
        for ( Artifact artifact : artifacts )
        {
            artifact.setArtifactHandler( mockArtifactHandler );
            result.add( artifact );
        }
        return result;
    }

    public Manifest getJarFileManifest( File jarFile )
        throws IOException
    {
        try ( JarFile jar = new JarFile( jarFile ) )
        {
            return jar.getManifest();
        }
    }

    @Test
    public void testParseOutputTimestamp()
    {
        MavenArchiver archiver = new MavenArchiver();

        assertThat( archiver.parseOutputTimestamp( null ) ).isNull();
        assertThat( archiver.parseOutputTimestamp( "" ) ).isNull();
        assertThat( archiver.parseOutputTimestamp( "." ) ).isNull();
        assertThat( archiver.parseOutputTimestamp( " " ) ).isNull();
        assertThat( archiver.parseOutputTimestamp( "_" ) ).isNull();

        assertThat( archiver.parseOutputTimestamp( "1570300662" ).getTime() ).isEqualTo( 1570300662000L );

        assertThat( archiver.parseOutputTimestamp( "2019-10-05T18:37:42Z" ).getTime() ).isEqualTo( 1570300662000L );
        assertThat( archiver.parseOutputTimestamp( "2019-10-05T20:37:42+02:00" ).getTime() ).isEqualTo(
                1570300662000L );
        assertThat( archiver.parseOutputTimestamp( "2019-10-05T16:37:42-02:00" ).getTime() ).isEqualTo(
                1570300662000L );

        // These must result in IAE because we expect extended ISO format only (ie with - separator for date and
        // : separator for timezone), hence the XXX SimpleDateFormat for tz offset
        // X SimpleDateFormat accepts timezone without separator while date has separator, which is a mix between
        // basic (no separators, both for date and timezone) and extended (separator for both)
        try
        {
            archiver.parseOutputTimestamp( "2019-10-05T20:37:42+0200" );
            fail();
        }
        catch ( IllegalArgumentException ignored )
        {
        }
        try
        {
            archiver.parseOutputTimestamp( "2019-10-05T20:37:42-0200" );
            fail();
        }
        catch ( IllegalArgumentException ignored )
        {
        }

        // These unfortunately fail although the input is valid according to ISO 8601
        // SDF does not allow strict telescoping parsing w/o permitting invalid input as depicted above.
        // One has to use the new Java Time API for this.
        try
        {
            archiver.parseOutputTimestamp( "2019-10-05T20:37:42+02" );
            fail();
        }
        catch ( IllegalArgumentException ignored )
        {
        }
        try
        {
            archiver.parseOutputTimestamp( "2019-10-05T20:37:42-02" );
            fail();
        }
        catch ( IllegalArgumentException ignored )
        {
        }
    }
}
