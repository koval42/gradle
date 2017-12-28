/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.language.cpp

import org.gradle.nativeplatform.fixtures.app.CppLib
import org.gradle.test.fixtures.archive.ZipTestFixture
import org.gradle.test.fixtures.maven.MavenFileRepository


class CppLibraryWithStaticLinkagePublishingIntegrationTest extends AbstractCppInstalledToolChainIntegrationTest implements CppTaskNames {
    def "can publish the binaries and headers of a library to a Maven repository"() {
        def lib = new CppLib()
        assert !lib.publicHeaders.files.empty
        assert !lib.privateHeaders.files.empty

        given:
        buildFile << """
            apply plugin: 'cpp-library'
            apply plugin: 'maven-publish'
            
            group = 'some.group'
            version = '1.2'
            library {
                baseName = 'test'
                linkage = [Linkage.STATIC]
            }
            publishing {
                repositories { maven { url 'repo' } }
            }
"""
        lib.writeToProject(testDirectory)

        when:
        run('publish')

        then:
        result.assertTasksExecuted(
            compileAndStaticLinkTasks(debug),
            compileAndStaticLinkTasks(release),
            ":generatePomFileForDebugPublication",
            ":generateMetadataFileForDebugPublication",
            ":publishDebugPublicationToMavenRepository",
            ":cppHeaders",
            ":generatePomFileForMainPublication",
            ":generateMetadataFileForMainPublication",
            ":publishMainPublicationToMavenRepository",
            ":generatePomFileForReleasePublication",
            ":generateMetadataFileForReleasePublication",
            ":publishReleasePublicationToMavenRepository",
            ":publish"
        )

        def headersZip = file("build/headers/cpp-api-headers.zip")
        new ZipTestFixture(headersZip).hasDescendants(lib.publicHeaders.files*.name)

        def repo = new MavenFileRepository(file("repo"))

        def main = repo.module('some.group', 'test', '1.2')
        main.assertPublished()
        main.assertArtifactsPublished("test-1.2-cpp-api-headers.zip", "test-1.2.pom", "test-1.2.module")
        main.artifactFile(classifier: 'cpp-api-headers', type: 'zip').assertIsCopyOf(headersZip)

        main.parsedPom.scopes.isEmpty()

        def mainMetadata = main.parsedModuleMetadata
        mainMetadata.variants.size() == 5
        def api = mainMetadata.variant("api")
        api.dependencies.empty
        api.files.size() == 1
        api.files[0].name == 'cpp-api-headers.zip'
        api.files[0].url == 'test-1.2-cpp-api-headers.zip'
        mainMetadata.variant("debug-link").availableAt.coords == "some.group:test_debug:1.2"
        mainMetadata.variant("debug-runtime").availableAt.coords == "some.group:test_debug:1.2"
        mainMetadata.variant("release-link").availableAt.coords == "some.group:test_release:1.2"
        mainMetadata.variant("release-runtime").availableAt.coords == "some.group:test_release:1.2"

        def debug = repo.module('some.group', 'test_debug', '1.2')
        debug.assertPublished()
        debug.assertArtifactsPublished(withStaticLibrarySuffix("test_debug-1.2"), "test_debug-1.2.pom", "test_debug-1.2.module")
        debug.artifactFile(type: staticLibraryExtension).assertIsCopyOf(staticLibrary("build/lib/main/debug/test").file)

        debug.parsedPom.scopes.isEmpty()

        def debugMetadata = debug.parsedModuleMetadata
        debugMetadata.variants.size() == 2
        def debugLink = debugMetadata.variant('debug-link')
        debugLink.dependencies.empty
        debugLink.files.size() == 1
        debugLink.files[0].name == staticLibraryName('test')
        debugLink.files[0].url == withStaticLibrarySuffix("test_debug-1.2")
        def debugRuntime = debugMetadata.variant('debug-runtime')
        debugRuntime.dependencies.empty
        debugRuntime.files.empty

        def release = repo.module('some.group', 'test_release', '1.2')
        release.assertPublished()
        release.assertArtifactsPublished(withStaticLibrarySuffix("test_release-1.2"), "test_release-1.2.pom", "test_release-1.2.module")
        release.artifactFile(type: staticLibraryExtension).assertIsCopyOf(staticLibrary("build/lib/main/release/test").file)

        release.parsedPom.scopes.isEmpty()

        def releaseMetadata = release.parsedModuleMetadata
        releaseMetadata.variants.size() == 2
        def releaseLink = releaseMetadata.variant('release-link')
        releaseLink.dependencies.empty
        releaseLink.files.size() == 1
        releaseLink.files[0].name == staticLibraryName('test')
        releaseLink.files[0].url == withStaticLibrarySuffix("test_release-1.2")
        def releaseRuntime = releaseMetadata.variant('release-runtime')
        releaseRuntime.dependencies.empty
        releaseRuntime.files.empty
    }
}
