# **Architectural Engineering of Real-Time Global Music Integration within the Minecraft Java Edition Environment**

The technical endeavor of embedding a global music streaming service directly into the Minecraft Java Edition client necessitates a departure from standard API-driven development toward a more sophisticated architecture based on metadata mirroring and stream extraction. For a developer who has already established a localized decoding and playback engine, the challenge shifts from the manipulation of local pulse-code modulation data to the orchestration of complex web-based workflows. This transformation must be achieved through a "jugaad" or workaround-centric approach to maintain a completely free and seamless user experience, bypassing the increasingly restrictive and expensive official developer portals of major platforms like Spotify and YouTube Music.1 The goal is a perfect user flow: search in-client, pick a result, and play it instantly, all while maintaining the game’s performance integrity.

## **Theoretical Framework for Unofficial Multimedia Orchestration**

Achieving a seamless search-and-play flow within a sandboxed environment like Minecraft requires a tri-layered architectural stack that decouples the user interface from the network and audio processing logic. This decoupling is essential because Minecraft’s main rendering thread is notoriously intolerant of blocking operations.4 The architectural stack consists of the Discovery Layer, the Resolution Layer, and the Audio Bridge.

The Discovery Layer focuses on metadata harvesting, utilizing extractors that mimic web browser behavior to retrieve track names, artist information, and album art without requiring API keys.5 The Resolution Layer serves as the "jugaad" hub, where non-playable metadata from services like Spotify is mirrored and resolved into playable stream URLs from platforms like YouTube or SoundCloud.7 Finally, the Audio Bridge handles the real-time decoding of these streams—typically in Opus or AAC formats—and feeds the resulting raw audio samples into the game’s native OpenAL system for output.9

### **Comparative Analysis of Multimedia Integration Libraries**

Selecting the appropriate foundation is critical for long-term maintenance and performance. The following table contrasts the primary libraries available for Minecraft developers to implement these layers.

| Library | Primary Architectural Role | Native Dependency | Protocol Support | Mirroring Support |
| :---- | :---- | :---- | :---- | :---- |
| LavaPlayer | Audio Decoding & Streaming | JNI (Native) | HTTP, DASH, HLS | Indirect (via Plugins) 12 |
| NewPipeExtractor | Metadata & Stream Resolution | None (Pure Java) | InnerTube, SoundCloud | Primary Search 13 |
| WaterMedia | Multimedia API & Rendering | LibVLC | Wide (VLC-based) | Platform Native 15 |
| LavaSrc | Metadata Mirroring Logic | LavaPlayer | Spotify, Apple, Deezer | Comprehensive 7 |
| Spotube Core | Authentication Workarounds | None (Dart/Logic) | Spotify TOTP | Token Bypassing 17 |

## **Metadata Harvesting: The Search Jugaad**

The search experience is the first touchpoint of the user flow. For a music player to feel "perfect," it must provide real-time suggestions and accurate results without the latency or cost associated with official APIs.1 In the current landscape, especially considering the 2025 and 2026 restrictions placed on Spotify’s developer platform, unofficial extraction has become the standard for open-source music mods.20

### **Bypassing Google and YouTube Music API Restrictions**

Official access to YouTube Music metadata through the Google Cloud Console is governed by a quota system that is easily exhausted by a popular mod, and it requires a complex OAuth2 setup for many features.22 The workaround involves the NewPipeExtractor, which utilizes the "InnerTube" API—the same internal API used by the official YouTube and YouTube Music web clients.13

By constructing requests that include specific headers and user-agent strings, the mod can query the /search and /suggestions endpoints of YouTube Music directly.5 This approach provides a rich JSON response containing StreamInfoItem objects. These objects are packed with the necessary data for a high-quality GUI: track titles, artist names, high-resolution thumbnail URLs, and durations.5 Furthermore, the SuggestionExtractor in NewPipe allows the developer to implement a "search-as-you-type" feature, where every keystroke in the Minecraft text field triggers a background request to fetch potential matches, significantly enhancing the flow's perceived "perfection".24

### **The Spotify Metadata Loophole**

Spotify’s integration is technically more complex because Spotify does not host the audio in a format easily accessible to third-party players without a Premium account and the official SDK.2 However, the metadata can be retrieved for free. A critical "jugaad" involves the fact that the Spotify web player at open.spotify.com generates an anonymous accessToken and clientId for users who are not logged in.6

By programmatically visiting the Spotify search page or using a specialized scraper like noauth, the mod can extract this temporary token.6 This token allows the client to make GET requests to api.spotify.com/v1/search for a limited time.6 While this method is restricted to public data and cannot access user libraries or make POST requests, it is ideal for a global search feature where the goal is simply to find a track to play.6

| Search Parameter | Spotify Official API | Spotify Scraping (Jugaad) | NewPipe (YT Music) |
| :---- | :---- | :---- | :---- |
| Authentication | OAuth2 / Client Secret | Anonymous Token | None (InnerTube) |
| Cost | Free (Limited) / Premium | Completely Free | Completely Free |
| Search Suggestions | Supported | Emulated | Native Support 24 |
| Metadata Richness | High (ISRC included) | High | Moderate (Platform Native) |

## **The Mirroring Engine: Resolving Non-Playable Metadata**

The most critical component of a "free" music player is the Mirroring Engine. Mirroring is the process of taking metadata resolved from a source that cannot be played (like Spotify) and using it to retrieve a playable stream from a source that can be accessed (like YouTube).7

### **ISRC-Based Precision Matching**

The highest fidelity of mirroring is achieved using the International Standard Recording Code (ISRC). Spotify’s metadata often includes this code, which acts as a unique fingerprint for a specific recording.7 The LavaSrc plugin for LavaPlayer utilizes this by taking the ISRC from a Spotify search result and performing a specialized YouTube search for that specific code.7

The logic follows a hierarchical fallback system:

1. Search YouTube for the ISRC.  
2. If no match is found, search YouTube Music for %ARTIST% \- %TITLE%.  
3. If no match is found, search standard YouTube for %ARTIST% \- %TITLE% %ALBUM%.  
4. If multiple results appear, prioritize those from "Official Artist Channels" or with a duration within a 2-second tolerance of the original Spotify metadata.7

This process must happen entirely in the background. When the user "picks" a song from the Spotify search list, the client should display a "Loading..." or "Resolving..." state while the mirroring engine finds the best match on YouTube.1

### **Managing Signature Decryption and DASH Streams**

Once a YouTube URL is identified through mirroring, the stream itself must be resolved. YouTube uses a rolling cipher to obfuscate its streaming URLs, which frequently changes to break third-party players.1 Libraries like LavaPlayer and NewPipeExtractor include built-in signature decrypters that are updated frequently by the community.12

Furthermore, YouTube audio is typically served as a DASH (Dynamic Adaptive Streaming over HTTP) stream. This means the audio and video are separate, and the audio is broken into chunks.5 The player must be capable of handling these chunked requests. LavaPlayer is particularly effective here, as it can handle the DASH manifest and stream the Opus-encoded audio packets directly, which are then passed to the decoder.12

## **Audio Plumbing: Bridging Streams to the Minecraft Engine**

The transition from a network stream to a sound heard in-game involves a complex pipeline of decoding and buffering. Since the developer has already created a local player, the focus must be on integrating the AudioSource from the network into the OpenAL buffer system.9

### **High-Performance Decoding with LavaPlayer**

LavaPlayer’s architecture is uniquely suited for Minecraft because it avoids the overhead of launching external processes like ffmpeg.1 Instead, it uses embedded native libraries to perform the decoding.12 The memory footprint is extremely low—approximately 350 KB per track—because it utilizes off-heap memory for the thread stack and audio buffers.12

For the "perfect" flow, the audio should be decoded into 20ms frames of PCM data. The choice of 20ms is standard across many high-efficiency codecs like Opus, which is the native format for most YouTube audio.9 By matching the input frame size to the output buffer size, the player can minimize resampling overhead and CPU spikes.12

### **The OpenAL Buffer Bridge and Stutter Prevention**

Minecraft’s native audio system, based on OpenAL, requires a constant supply of PCM data. If the network jitters and the buffer empties, the audio will stutter or stop.36 To prevent this, a "Triple Buffering" or "Multi-Buffering" strategy is required.11

The developer must generate one OpenAL source and a set of at least 3 to 6 buffers.36 The flow operates as follows:

1. LavaPlayer decodes a 20ms frame and provides it as a ByteBuffer.9  
2. The mod fills an OpenAL buffer using alBufferData().10  
3. The filled buffer is added to the source’s queue via alSourceQueueBuffers().11  
4. The source begins playing.  
5. In a separate thread, the mod polls alGetSourcei(source, AL\_BUFFERS\_PROCESSED) to see if a buffer has finished playing.11  
6. When a buffer is processed, it is "un-queued," refilled with the next 20ms frame from LavaPlayer, and re-queued immediately.11

The mathematical requirement for this queue can be defined as:

![][image1]  
Where ![][image2] is the minimum number of buffers in the queue, ![][image3] is the maximum network latency, ![][image4] is the decoding latency, and ![][image5] is the duration of a single audio frame (e.g., 20ms).36

## **Navigating the 2025-2026 Platform Security Landscape**

As of early 2026, music streaming platforms have significantly increased their security measures to combat third-party scraping and unauthorized API usage. These changes directly impact the "jugaad" methods used in Minecraft mods.

### **Spotify’s February 2026 Migration and its Impact**

In February 2026, Spotify introduced a major overhaul of its developer platform, including the requirement for a Premium account for any "Development Mode" Client ID and the deprecation of numerous public endpoints.20 Critically for mod developers, the limit on search results was reduced from 50 to 10 per request, and many metadata fields like "popularity" were removed from the public response.38

To maintain a "perfect" user flow under these conditions, the mod must implement local pagination logic. If the user scrolls past the first 10 results, the mod must automatically trigger a new request using the offset parameter to fetch the next set.38 Furthermore, the open.spotify.com/get\_access\_token endpoint, previously a standard for anonymous tokens, now requires a Time-based One-Time Password (TOTP).18

### **Reverse Engineering the Spotify TOTP**

To bypass the "Invalid TOTP" error that began appearing in early 2025, modern implementations like those found in the Spotube project have integrated a TOTP generator.18 This involves fetching a server-time from Spotify’s public API and using a rotating secret found within the Spotify Web Player’s obfuscated JavaScript.18

The process of generating the required 6-digit code involves:

1. Fetching the current Unix timestamp from open.spotify.com/api/server-time.34  
2. Dividing the timestamp (in seconds) by 30 and flooring the result.34  
3. Applying a HMAC-SHA1 algorithm with the secret key and the time step.34  
4. Submitting this code along with the anonymous client ID to the /api/token endpoint to receive a valid access token for searching.18

| Platform Update | Security Change | Workaround (Jugaad) | Reliability |
| :---- | :---- | :---- | :---- |
| Spotify (Feb 2026\) | Dev ID Premium Required | Anonymous Token Scraping | Moderate 20 |
| Spotify (2025) | TOTP on Token Endpoint | Local TOTP Generation | High 18 |
| YouTube (Rolling) | Cipher Signature Rotation | Community Patcher (LavaPlayer) | Very High 12 |
| YT Music (2025) | InnerTube Header Hardening | Piped API Proxying | High 17 |

## **User Interface Design and Flow Perfection**

A perfect user flow is not just about the underlying technology; it is about how that technology is presented within the Minecraft GUI. The search-pick-play sequence must feel as responsive as the base game menus.1

### **Asynchronous GUI Rendering**

In Minecraft, the GUI is rendered on every frame. Any network wait will "freeze" the screen.1 Therefore, the SearchInfo.getInfo() call must be wrapped in a CompletableFuture.4 The search results should be stored in a thread-safe list that the GUI thread reads from.26

As search results are populated, the mod must also asynchronously load thumbnails. Minecraft does not natively support loading external images into textures during a frame.32 The developer must implement a system that:

1. Downloads the thumbnail image into a BufferedImage.  
2. Schedules a task to run on the main Minecraft thread to upload that image to the GPU as a NativeImage or DynamicTexture.  
3. Caches the resulting texture ID so it doesn't need to be reloaded if the user scrolls back up.1

### **The Role of Search Suggestions**

To minimize typing effort, the SuggestionExtractor provides a crucial link in the flow.24 As the user types, a small dropdown list should appear with suggestions from YouTube Music. Clicking a suggestion should immediately trigger a full search.24 This mimics the behavior of modern web browsers and professional music apps, fulfilling the user's requirement for a "perfect" flow.1

## **Technical Resilience and Infrastructure Management**

A "free" player is only useful if it works consistently. This requires managing rate limits and providing hosting options for shared environments like multiplayer servers.

### **Rate Limiting and Client-Side Throttling**

Platforms like YouTube and Spotify monitor the frequency of requests from a single IP address. If the mod makes too many metadata or stream requests, the user’s IP may be flagged as a bot.19 To mitigate this, the mod should:

* Cache search results for at least one hour.19  
* Throttle search suggestion requests to no more than one per 300ms.  
* Implement a "Mirroring Cache" that stores the YouTube URL resolved for a specific Spotify ISRC, so future users playing the same song don't need to repeat the resolution process.7

### **Decentralized Alternatives: The Piped API**

If client-side extraction becomes unreliable due to aggressive IP blocking, the mod can be configured to use the Piped API.17 Piped is a privacy-friendly frontend for YouTube that provides its own API for search and stream resolution.17 Because there are many public Piped instances, the mod can "load balance" requests across multiple servers, ensuring that no single client IP is ever overused.39

## **Conclusion: Strategic Implementation for Long-Term Viability**

The architectural blueprint for a free, high-performance music player in Minecraft is centered on the synergy between metadata harvesting and stream resolution. By utilizing the NewPipeExtractor for YouTube Music discovery and the mirroring logic of LavaSrc for Spotify integration, the developer can offer a vast library of music without the burden of premium API fees or authentication barriers.1

The technical success of the project relies on the robust implementation of an asynchronous "Fetch-Decode-Bridge" pipeline, where LavaPlayer handles the native decoding and an OpenAL buffer queue ensures stutter-free audio within the game’s rendering cycle.9 As platforms like Spotify continue to harden their security in 2026, the mod’s resilience will depend on its ability to adapt "jugaad" solutions—such as local TOTP generation and InnerTube API manipulation—to maintain the open-access loopholes that currently exist.17

Ultimately, the goal is to hide these complexities from the player. A well-designed Minecraft mod acts as a silent orchestrator, managing complex network handshakes and native memory allocation in the background, allowing the user to simply search, pick, and enjoy their music in the seamless flow of their virtual world.1

#### **Works cited**

1. marcjc1173/radio\_mod: A Minecraft Forge mod that adds a ... \- GitHub, accessed April 25, 2026, [https://github.com/marcjc1173/radio\_mod](https://github.com/marcjc1173/radio_mod)  
2. Web API \- Spotify for Developers, accessed April 25, 2026, [https://developer.spotify.com/documentation/web-api](https://developer.spotify.com/documentation/web-api)  
3. Getting started with Web API \- Spotify for Developers, accessed April 25, 2026, [https://developer.spotify.com/documentation/web-api/tutorials/getting-started](https://developer.spotify.com/documentation/web-api/tutorials/getting-started)  
4. Playing Audio \- Javacord, accessed April 25, 2026, [https://javacord.org/wiki/advanced-topics/playing-audio.html](https://javacord.org/wiki/advanced-topics/playing-audio.html)  
5. Quick Start \- NewPipe Extractor \- Mintlify, accessed April 25, 2026, [https://www.mintlify.com/TeamNewPipe/NewPipeExtractor/quickstart](https://www.mintlify.com/TeamNewPipe/NewPipeExtractor/quickstart)  
6. kaangiray26/noauth: Spotify's public API without authentication \- GitHub, accessed April 25, 2026, [https://github.com/kaangiray26/noauth](https://github.com/kaangiray26/noauth)  
7. topi314/LavaSrc: A collection of additional Lavaplayer/Lavalink Sources \- GitHub, accessed April 25, 2026, [https://github.com/topi314/LavaSrc](https://github.com/topi314/LavaSrc)  
8. Lavalink V4 Advanced | DisCatSharp Docs, accessed April 25, 2026, [https://docs.dcs.aitsys.dev/articles/modules/audio/lavalink\_v4/advanced](https://docs.dcs.aitsys.dev/articles/modules/audio/lavalink_v4/advanced)  
9. lavaplayer/demo-jda/src/main/java/com/sedmelluq/discord/lavaplayer/demo/jda/AudioPlayerSendHandler.java at master \- GitHub, accessed April 25, 2026, [https://github.com/sedmelluq/lavaplayer/blob/master/demo-jda/src/main/java/com/sedmelluq/discord/lavaplayer/demo/jda/AudioPlayerSendHandler.java](https://github.com/sedmelluq/lavaplayer/blob/master/demo-jda/src/main/java/com/sedmelluq/discord/lavaplayer/demo/jda/AudioPlayerSendHandler.java)  
10. OpenAL short example \- ffainelli, accessed April 25, 2026, [https://ffainelli.github.io/openal-example/](https://ffainelli.github.io/openal-example/)  
11. LWJGL streaming sound with OpenAL, accessed April 25, 2026, [https://bedroomcoders.co.uk/posts/76](https://bedroomcoders.co.uk/posts/76)  
12. lavaplayer/README.md at main \- GitHub, accessed April 25, 2026, [https://github.com/lavalink-devs/lavaplayer/blob/main/README.md](https://github.com/lavalink-devs/lavaplayer/blob/main/README.md)  
13. StreamExtractor (NewPipe Extractor v0.26.1) \- GitHub Pages, accessed April 25, 2026, [https://teamnewpipe.github.io/NewPipeExtractor/javadoc/org/schabi/newpipe/extractor/stream/StreamExtractor.html](https://teamnewpipe.github.io/NewPipeExtractor/javadoc/org/schabi/newpipe/extractor/stream/StreamExtractor.html)  
14. TeamNewPipe / NewPipeExtractor Download \- JitPack, accessed April 25, 2026, [https://javadoc.jitpack.io/p/teamnewpipe/newpipeextractor](https://javadoc.jitpack.io/p/teamnewpipe/newpipeextractor)  
15. WATERMeDIA \- MWP Wiki, accessed April 25, 2026, [https://modwiki.miraheze.org/wiki/WATERMeDIA](https://modwiki.miraheze.org/wiki/WATERMeDIA)  
16. GitHub \- WaterMediaTeam/watermedia: Library and API for Multimedia, Powered by LibVLC. Working on pure JAVA and all Minecraft Modloaders, accessed April 25, 2026, [https://github.com/WaterMediaTeam/watermedia](https://github.com/WaterMediaTeam/watermedia)  
17. GitHub \- KRTirtho/spotube: Open source music streaming app\! Available for both desktop & mobile\!, accessed April 25, 2026, [https://github.com/krtirtho/spotube](https://github.com/krtirtho/spotube)  
18. \[Notice\] open.spotify.com/get\_access\_token No longer works · Issue \#1475 · librespot-org/librespot \- GitHub, accessed April 25, 2026, [https://github.com/librespot-org/librespot/issues/1475](https://github.com/librespot-org/librespot/issues/1475)  
19. Spotify Scraper \- Apify, accessed April 25, 2026, [https://apify.com/web-scraper/spotify-scraper](https://apify.com/web-scraper/spotify-scraper)  
20. Update on Developer Access and Platform Security, accessed April 25, 2026, [https://developer.spotify.com/blog/2026-02-06-update-on-developer-access-and-platform-security](https://developer.spotify.com/blog/2026-02-06-update-on-developer-access-and-platform-security)  
21. Changes to Spotify API : r/webdev \- Reddit, accessed April 25, 2026, [https://www.reddit.com/r/webdev/comments/1rflyiz/changes\_to\_spotify\_api/](https://www.reddit.com/r/webdev/comments/1rflyiz/changes_to_spotify_api/)  
22. Scraping YouTube data without using an API \- Dani Madrid-Morales, accessed April 25, 2026, [https://danimadrid.net/blog/scraping\_youtube\_without\_api.html](https://danimadrid.net/blog/scraping_youtube_without_api.html)  
23. YouTube Data API \- Google for Developers, accessed April 25, 2026, [https://developers.google.com/youtube/v3](https://developers.google.com/youtube/v3)  
24. NewPipeExtractor-KMP — KMP library for Android JVM, JVM, Kotlin/Native, Wasm | Klibs.io, accessed April 25, 2026, [https://klibs.io/project/yushosei/NewPipeExtractor-KMP](https://klibs.io/project/yushosei/NewPipeExtractor-KMP)  
25. SearchExtractor (NewPipe Extractor v0.26.0), accessed April 25, 2026, [https://teamnewpipe.github.io/NewPipeExtractor/javadoc/org/schabi/newpipe/extractor/search/SearchExtractor.html](https://teamnewpipe.github.io/NewPipeExtractor/javadoc/org/schabi/newpipe/extractor/search/SearchExtractor.html)  
26. NewPipeExtractor/extractor/src/main/java/org/schabi/newpipe/extractor/search/SearchInfo.java at dev \- GitHub, accessed April 25, 2026, [https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/main/java/org/schabi/newpipe/extractor/search/SearchInfo.java](https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/extractor/src/main/java/org/schabi/newpipe/extractor/search/SearchInfo.java)  
27. Implementing a Service \- NewPipe Development Documentation \- GitHub Pages, accessed April 25, 2026, [https://teamnewpipe.github.io/documentation/03\_Implement\_a\_service/](https://teamnewpipe.github.io/documentation/03_Implement_a_service/)  
28. Spotify Search API. In this article we will discuss about… | by QA-init | Medium, accessed April 25, 2026, [https://medium.com/@QA-initi/spotify-search-api-50e966e43bd7](https://medium.com/@QA-initi/spotify-search-api-50e966e43bd7)  
29. Spotify Web API \- Requests without Token Authentication \- Stack Overflow, accessed April 25, 2026, [https://stackoverflow.com/questions/38126565/spotify-web-api-requests-without-token-authentication](https://stackoverflow.com/questions/38126565/spotify-web-api-requests-without-token-authentication)  
30. Headless Selenium option to pull api information from Spotify · Issue \#2597 \- GitHub, accessed April 25, 2026, [https://github.com/spotDL/spotify-downloader/issues/2597](https://github.com/spotDL/spotify-downloader/issues/2597)  
31. Spotify links yield no data on loadtracks · topi314 LavaSrc · Discussion \#124 \- GitHub, accessed April 25, 2026, [https://github.com/topi314/LavaSrc/discussions/124](https://github.com/topi314/LavaSrc/discussions/124)  
32. JonII7/PlayerMusic: PlayMusic in Minecraft \- GitHub, accessed April 25, 2026, [https://github.com/JonII7/PlayerMusic](https://github.com/JonII7/PlayerMusic)  
33. Music Player \- Minecraft Mods \- CurseForge, accessed April 25, 2026, [https://www.curseforge.com/minecraft/mc-mods/music-player](https://www.curseforge.com/minecraft/mc-mods/music-player)  
34. \[Notice\] open.spotify.com/get\_access\_token No longer works · Issue \#1475 · librespot-org/librespot \- GitHub, accessed April 25, 2026, [https://github.com/librespot-org/librespot/issues/1475?timeline\_page=1](https://github.com/librespot-org/librespot/issues/1475?timeline_page=1)  
35. JDA (Java Discord API) & Lavaplayer AudioPlayer Mixing \- Stack Overflow, accessed April 25, 2026, [https://stackoverflow.com/questions/79742321/jda-java-discord-api-lavaplayer-audioplayer-mixing](https://stackoverflow.com/questions/79742321/jda-java-discord-api-lavaplayer-audioplayer-mixing)  
36. Play sound with OpenAL(Stream) \- c++ \- Stack Overflow, accessed April 25, 2026, [https://stackoverflow.com/questions/14932004/play-sound-with-openalstream](https://stackoverflow.com/questions/14932004/play-sound-with-openalstream)  
37. Web API Changelog \- February 2026 \- Spotify for Developers, accessed April 25, 2026, [https://developer.spotify.com/documentation/web-api/references/changes/february-2026](https://developer.spotify.com/documentation/web-api/references/changes/february-2026)  
38. February 2026 Web API Dev Mode Changes \- Migration Guide \- Spotify for Developers, accessed April 25, 2026, [https://developer.spotify.com/documentation/web-api/tutorials/february-2026-migration-guide](https://developer.spotify.com/documentation/web-api/tutorials/february-2026-migration-guide)  
39. API Documentation \- Piped, accessed April 25, 2026, [https://docs.piped.video/docs/api-documentation/](https://docs.piped.video/docs/api-documentation/)  
40. WATERMeDIA: Multimedia API \- WM/3.0.0-beta.12 \- Minecraft Mods \- CurseForge, accessed April 25, 2026, [https://www.curseforge.com/minecraft/mc-mods/watermedia/files/7877493](https://www.curseforge.com/minecraft/mc-mods/watermedia/files/7877493)  
41. SpotifyScraper Documentation, accessed April 25, 2026, [https://spotifyscraper.readthedocs.io/](https://spotifyscraper.readthedocs.io/)  
42. Spotify Users Search and Profile Scraper \- Apify, accessed April 25, 2026, [https://apify.com/apiharvest/spotify-users-search-and-profile-scraper](https://apify.com/apiharvest/spotify-users-search-and-profile-scraper)  
43. YouTube Search Engine Results API \- SerpApi, accessed April 25, 2026, [https://serpapi.com/youtube-search-api](https://serpapi.com/youtube-search-api)

[image1]: <data:image/png;base64,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>

[image2]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACcAAAAYCAYAAAB5j+RNAAACHElEQVR4Xu2WMUhVURzGv9BAyWhQFMFJXMxCxWwIcXAQFF3UBisVFExwCSJEJ0EaHHSIIAwaGlIcFCNBUAjRoYjGnDLSSIUEnZwE7fv4n4vPczVv0rsNvg9+nHfPd947//c/5/zPBVK6gMomtaSFFJO043b8ukSqyScyR+473pNVUnk0NF5dJsPkO8JByHtJdkm55yVdmvwF2SG3PS/QDVhwz2EZjk095MC1pymfrJMVkuN5SVMR2SRfSK7nJSoITuhzLBokh679kwrIT8QYXBZZJPuk6rgVknyNWyZXPS8p+pul6kO0DJ8kZX3EtZEVBHdWNq6Rj2SblHheFJXC5lAxjyydOp2+byTP8xL1AHaaH/tGMpVOJmD1S3VM9W4IdhtoGfSsuidfRVjPgbrIDGmHLfUCaSZ3yCR5BVvGDPKETJObpIy8hv1+B3kHmzMTJ0gVP5i8ldx1/W2kl6yRUYS/rEL8hozDgtbSbZAm5w+QZ+6ztswsqXDPGvODFJIrZJ7UOS8kXVdfYafxKew+fQvLYA0sEF38iZmTlAEdFEn7dwlHm1798iVVBWU5CK4RViXUL2mc+k6VJr4FewvRwG5YoIEanJ8oP7hF10r/NDhfnWQPdgi0PFMIL+1/C057QHVNaE/6LwQPyRb5TO6RMdifUatn9esNR/e19t8v2B+sJx9gZUn9j2C/oz4dlkjSRu2HZe2656WU0nn0GzGzcdh7hzs+AAAAAElFTkSuQmCC>

[image3]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACEAAAAYCAYAAAB0kZQKAAABgUlEQVR4Xu3VvytHURjH8Ud+FJFfRTIpBkWSkYEyWJRiohgMdgabpBSDhYlIBgwUk8UiI/4FJYlBRiXy4/10zvW993C/ly9Ohu+nXt3bee7wnHufzhXJJpv06cItXkMecYjK0HNesooX9LgFXynHKS5QGy35SyPusIs8p+YtQ2JmYdIt+MwintDhFnwlmIdzVDs1b0mahwLku4u/nWAext0CycEMmtzCN9KNNnfRjZ4PcfNQjzUUuoUvRjcxKwlNpDsf9BOsiHlTmlHsYxjTYk7UPlvTtGMd2+hHEebxgD3Moez96VC0w3v5OA81Yhq4RJ1d011tYktMgy04ETPMrTgQc8wrvddPWCym8U/fhH6na0n9K55xZel9sK67Cje3IamzRBs9sld95ccYsPS+VxKayDRxTehcac1NuIkqVETLmSWuCf3p6e5Lba0BzRJtotNef5Qx3OAMg1gWM0961TmYwA5GMCWmIZ2jBSyJOQK0qT+PDmzwNsIpQa67mM2/yRskPU1uS+SLHQAAAABJRU5ErkJggg==>

[image4]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACIAAAAYCAYAAACfpi8JAAABnklEQVR4Xu2VPShHURjGX4UoJRkkEeUjYZBMJIqymFhksBjMDEqSlGS1kY/+E2G2WGSwWGQwKCXJIqMS+Xge7znd8z9/H7n+nVL3qV+d+z73473nPJ0jkijR79QD7sCbwxM4AKXOfcG0Bl5Bv2+EVAk4AVegIt0Kq0ZwD/ZArucF1YhoNqZ8I7SWwTPo9I2Qsvm4BGWeF1Q/5SMf5Hm1BnAqGu7ydCu+bD4mfAPKAfOg2TegNtFmstYI94+v8lEL1kGhb0iWG/lu/+ByrIrOmFU92AA7YA6cSdRIB9gEW2BQdDZJl6ltgxnJXOYP8a8eJDMffDmbuAY1psbZOQTV5ppHw7nova1gX/RIIBxzOfvAkal1gwtQJY56wa1EZ8sLuDFwbOv8E9sgM+Q27C7NgugHhwwcD4guO72siptdyrl2G+EHXc+KtWm/+FfxMDwWzRXVLlFG6HEWio1XB1rAuOgy2bA3gUozji2GbFE0O8OigX0EK6IZmAS7YBTMijbFBpbMM2Ois/NpWOOoABSJvpCbnSvW7Ky4ss8k+h96B7gRUT/Ye3lgAAAAAElFTkSuQmCC>

[image5]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAC4AAAAYCAYAAACFms+HAAACKklEQVR4Xu2WTyhnURTHj1BkJqH8aaZmSsjKwlhMSDQLs7AzFkOmCLOwoZjMrIYNKyWLaVJMoggzuykzZWFBM9LsSMkvKTsLjVJqxvfr3Oddlx/yo/crv299evee++575957zr1XJKaY7oeqwCbYviYvtFuwigPDYAo8NXVqBPwD1aYeDypACJQYW6DKAtMg07Klgd+iTj6y7A/AOHhs2QITl73DsRWBfTADEiw7BzQEHlq2wPQK5Du2evAfvHPsGaBV/HCKOjG+j0CZ2xDNChfftjjj6SDRbQhSxeBAzse3pyfgK5gwz6SzzcEpXHx7YnLynVxQKVES73RiVC6P7y+gxjUGravi+yXYAvOgC7wFP0GnaNjUgmTwAfSLTkLpSU893HjQfQZ1YA58AnmgB3wHjeKvYDYYBGOgD6QY+4W6Kr4pe8aZnHSYDr0BvaJnwIZ58nDjwArM+zxxd8RfTe5edDhV9FBbAYWiBx2/Wy46EDrebvqcinv4KtgTjW2Pv2BNdDC23FBx6xSvB3SgASyL/w0+/4AcU2cevTdl2tjGdzgwDrBFdBUHRP8TkVxH3Tp3nUVRp7llfpPLHfc2AdtxhmTI2G5NrqNunY54s8Oc+QGawHO5vuMMsV+ifSjmDQdzYzWLJueSKbeBXdFkZZ16Jhp6TNxuMAsWRAfH8qHolvoarBtYpo33o0nRRKbT7McrxkfRnLlzMcZ5r+EzEjExuWqRfiem+6tj/tV1P/BLB4EAAAAASUVORK5CYII=>