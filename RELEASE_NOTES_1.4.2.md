# 1.4.2 candidate

- Discover stripped wood restoration mappings from the running server, including Poplar logs and wood on Minecraft 26.3, while retaining older server support.
- Support Copper Shovels and Copper Hoes for PathBack and FarmBack when available.
- Add a regression test for every available stripped material, including explicit Poplar checks.
- Add `spigot.version` to the build so each supported API can be checked without modifying the POM.

This candidate compiles against Spigot APIs 26.1, 26.1.1, 26.1.2, 26.2, and 26.3. Java 25 is required by 26.x servers; the plugin keeps Java 21 bytecode for older servers. API compilation is not a full gameplay compatibility guarantee. See the task compatibility report for exact runtime coverage and limitations.
