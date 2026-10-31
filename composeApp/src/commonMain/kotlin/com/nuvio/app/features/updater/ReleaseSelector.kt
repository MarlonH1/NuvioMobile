package com.nuvio.app.features.updater

internal object ReleaseSelector {
    private val prereleaseNamePattern = Regex(
        "(?:^|[\\s._-])(alpha|beta|rc|preview)(?:[\\s._-]|$)",
        RegexOption.IGNORE_CASE
    )

    fun eligibleReleases(
        releases: List<GitHubReleaseDto>,
        channel: UpdateChannel
    ): List<GitHubReleaseDto> = forkReleases(releases) + releases
        .asSequence()
        .filterNot(GitHubReleaseDto::draft)
        .filter { ForkBuilds.stamp(it.tagName) == null }
        .mapNotNull { release ->
            val version = releaseVersion(release) ?: return@mapNotNull null
            ReleaseCandidate(
                release = release,
                version = version,
                prerelease = isPrerelease(release, version)
            )
        }
        .filter { candidate -> channel == UpdateChannel.BETA || !candidate.prerelease }
        .sortedByDescending(ReleaseCandidate::version)
        .map(ReleaseCandidate::release)
        .toList()

    /**
     * This fork's releases (tag `shad-<commit time>-<sha>`) carry no version number; newest
     * commit first. They come before versioned releases.
     */
    private fun forkReleases(releases: List<GitHubReleaseDto>): List<GitHubReleaseDto> = releases
        .filter { !it.draft && ForkBuilds.stamp(it.tagName) != null }
        .sortedByDescending { ForkBuilds.stamp(it.tagName) }

    private fun releaseVersion(release: GitHubReleaseDto): SemanticVersion? =
        VersionUtils.parse(release.tagName) ?: VersionUtils.parse(release.name)

    private fun isPrerelease(
        release: GitHubReleaseDto,
        version: SemanticVersion
    ): Boolean = release.prerelease ||
        version.prerelease.isNotEmpty() ||
        prereleaseNamePattern.containsMatchIn(release.name.orEmpty())

    private data class ReleaseCandidate(
        val release: GitHubReleaseDto,
        val version: SemanticVersion,
        val prerelease: Boolean
    )
}
