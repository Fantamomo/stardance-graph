package com.fantamomo.hc.stardancegraph.util.exceptions

// a special exception that is thrown by the ProjectParser if the project is a ghost project
//
// "ghost project" is a name that i created for projects that exists but have no author
// like those:
// - https://stardance.hackclub.com/projects/5028
// - https://stardance.hackclub.com/projects/4607
// - https://stardance.hackclub.com/projects/4952
// - and many more (in fact, 1,578)
// idk how those projects got created, but due to our parse structure, we fail to parse them
// but we are still retrying to parse them 3 times, which costs us a lot of requests
class GhostProjectException : Exception()