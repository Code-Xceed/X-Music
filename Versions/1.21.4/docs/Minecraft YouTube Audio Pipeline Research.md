# **Architectural Design and Optimization of Native YouTube Audio Streaming Pipelines within Minecraft Fabric 1.21.5 Environments**

The integration of high-fidelity, low-latency YouTube audio streaming into the Minecraft client architecture requires a sophisticated orchestration of extraction logic, network handling, and native audio processing. Within the current technical landscape, particularly following the major architectural shifts implemented by Google in late 2024 and early 2025, the reliability of legacy streaming methods has degraded significantly due to the widespread enforcement of Proof-of-Origin (PO) tokens and the centralization of media delivery under the InnerTube API.1 For a Fabric-based mod targeting Minecraft 1.21.5, the primary challenge is to construct a pipeline that balances the resource constraints of the Java Virtual Machine (JVM) with the real-time requirements of the OpenAL audio engine, all while navigating the complexities of YouTube's burst-based "Trickle" delivery mechanism.4

The fundamental objective of this research is to define a system capable of resolving nearly 100% of standard YouTube search results into a playable stream with an initial audible latency of under 10 seconds, while maintaining absolute stability across various network conditions and format variances.7 Achieving this involves moving beyond simple CLI-based downloads and adopting a hybrid, state-aware architecture that treats streaming as a continuous lifecycle of resolution, pre-buffering, and managed decoding.

## **The Evolution of the YouTube Extraction Ecosystem and the Proof-of-Origin (PO) Token**

The modern era of YouTube media extraction is defined by the transition from the publicly documented Data API v3 toward the internal "InnerTube" API. While the official API is governed by strict quota limits and is primarily designed for metadata retrieval, InnerTube serves the actual client applications—web, mobile, and living-room—offering access to the underlying media manifests (DASH and HLS) that allow for direct streaming.3 However, as of late 2024, YouTube has aggressively rolled out "BotGuard" and "DroidGuard" attestation requirements. These mechanisms generate a Proof-of-Origin (PO) Token, which attests that the request is originating from a legitimate, unautomated client.1

The mechanism of the PO-Token is deeply tied to the visitor data or session identity of the requester. In logged-out scenarios, the token is bound to a Visitor ID found in the VISITOR\_INFO1\_LIVE cookie or the visitorData value in InnerTube responses. In logged-in scenarios, the token is cryptographically bound to the account’s Data Sync ID.1 Without a valid PO-Token, requests for stream URLs (the videoplayback endpoints on Google Video Servers) frequently return HTTP 403 Forbidden errors or result in the immediate blocking of the user's IP address.1

For the Minecraft mod's YouTubeStreamResolver.java, this means that simple URL scraping is no longer viable. The architecture must integrate a PO-Token provider. Modern solutions like bgutil-ytdlp-pot-provider or rustypipe-botguard function by running a specialized JavaScript environment (often using the Rhino or GraalJS engines) to solve the BotGuard challenge and return a token that must be appended to the stream request.11 The implications for the mod are significant: the extraction layer must now support either a local JavaScript execution environment or a call to an external, lightweight POT server to ensure the resolution of media formats like ITAG 251 (Opus) and ITAG 140 (AAC).2

### **Comparative Analysis of Core Extraction Frameworks**

The current stack's reliance on yt-dlp and Piped/Invidious fallback represents a standard but increasingly fragile approach. A more robust architecture should consider the specific strengths of Java-native vs. external-process extractors.

| Framework | Implementation Mode | Coverage Potential | Performance (Latency) | Reliability Mechanism |
| :---- | :---- | :---- | :---- | :---- |
| **NewPipeExtractor** | Java (Rhino/GraalJS) | High (Native YouTube Client) | Very High (No fork overhead) | Native Cipher/Signature rotation.13 |
| **LavaPlayer** | Java with JNI | High (Optimized for Discord) | High (Single-process stream) | Integrated Opus-to-PCM bypass.15 |
| **yt-dlp** | External Process (Python) | Absolute (Fastest updates) | Moderate (Forking overhead) | Extensive community-maintained extractors.16 |
| **InnerTube Direct** | Raw HTTP/JSON | High (Requires POT) | Highest (Direct API calls) | Requires manual manifest parsing.3 |
| **Invidious/Piped** | Proxy API | Moderate (Instance-dependent) | Moderate (Double-hop latency) | IP-masking and instance rotation.18 |

The investigation suggests that the NewPipeExtractor is particularly suited for a Minecraft Fabric mod because it is written almost entirely in Java (99.8%) and uses the Rhino engine to handle JavaScript challenges, which aligns with the mod’s existing Java-based architecture.13 Conversely, while yt-dlp provides the most resilient coverage against YouTube's frequent code changes, the overhead of invoking a Python process for every track resolution can add 500ms to 2s to the initial startup latency, especially on systems with limited CPU resources.17

## **Optimizing the Stream Resolution Pipeline for Low Latency**

Latency in a media mod is perceived as the delay between the "click" and the "sound." This delay is composed of several stages: search, metadata resolution, stream URL resolution, FFmpeg probing, and initial buffer filling. To reach the 2-10 second target, each of these stages must be optimized.

### **The Mechanism of the YouTube "Trickle" and JIT Delivery**

Google research into the "Trickle" mechanism reveals that YouTube delivery follows two distinct phases: the startup phase and the throttling phase.5 During the startup phase, the server delivers the first 30 to 40 seconds of the audio stream as fast as possible to fill the client-side playback buffer. Following this, it enters a throttling phase where data is sent at 125% of the media's encoding rate.5

The mod's YouTubeNativeBackend.java must exploit this burst. By using FFmpeg with the \-fflags nobuffer and \-avioflags direct flags, the pipeline can ingest this initial burst without the decoder itself adding artificial delay.21 Furthermore, the common default of a 5MB probesize in FFmpeg is often unnecessary for well-known audio containers like WebM/Opus. Reducing probesize to as low as 32KB and analyzeduration to 0 allows FFmpeg to start pushing PCM frames to the AudioEngine almost immediately after the TCP handshake is completed.21

### **Mathematical Analysis of OpenAL Buffering and Latency**

The interaction between the FFmpeg pipe and the OpenAL output must be strictly managed to avoid "silent starts" or "choppy audio." In a standard configuration, OpenAL utilizes a series of queued buffers. If the buffer size is too small, the JVM's Garbage Collection (GC) pauses may cause the audio source to starve, resulting in audible stuttering.4

Let ![][image1] be the time until the first audible sound. It can be modeled as:

![][image2]  
Where ![][image3] is the required buffer size before playback begins, and ![][image4] is the network speed during the initial Trickle phase.5 For high-fidelity 48kHz stereo 16-bit audio, the data rate is:

![][image5]  
To survive a 200ms GC pause or network jitter event, the mod needs at least ![][image6] bytes of PCM data in the buffer. However, the investigation of OpenAL Soft issues suggests that 3 periods (60ms total) is often insufficient for problematic hardware, and a default of 4 to 6 periods (80-120ms) is recommended for maximum compatibility across various systems.4

## **Reliability Strategies: Surviving Rate Limits and Expired URLs**

A major failure mode in the current stack is the expiration of stream URLs. YouTube's videoplayback URLs contain an expire timestamp, typically set to 6 hours from the time of generation.25 While the stream itself can often exceed this duration if the connection remains open, the URL cannot be used to *start* or *resume* a stream once it has expired.

### **Just-In-Time (JIT) Resolution and Authentication**

The "Hybrid Model" proposed for the YouTubeDownloadManager.java and YouTubeStreamResolver.java should implement a "Just-In-Time" resolution policy. Rather than resolving stream URLs at the time a search result is added to a queue, the system should only resolve the metadata (title, thumbnail, video ID). The actual stream URL resolution should be deferred until 10-15 seconds before the track is scheduled to play. This prevents the "expired URL" issue for long-running queues and ensures that the PO-Token used is as fresh as possible.1

The use of cookies and authenticated extraction is a powerful tool for improving coverage. Research confirms that guest sessions are limited to roughly 300 videos per hour, while authenticated accounts can access up to 2000\.27 However, this carries the risk of account banning if the request rate is excessive.27 For a production mod, the system should allow users to optionally import cookies from their browser via a private/incognito session to avoid frequent cookie rotation, which YouTube uses as a security measure to invalidate automated sessions.1

### **Resilience via Fallback Architecture**

The current fallback order (yt-dlp \-\> Piped/Invidious) is logical but can be improved by adding a native Java layer.

| Tier | Component | Function | Failure Condition |
| :---- | :---- | :---- | :---- |
| **Tier 1** | Local Cache | Instant playback from YouTubeDownloadManager | File not found or corrupted. |
| **Tier 2** | Native InnerTube | Direct stream using NewPipeExtractor \+ POT | 403 Forbidden or cipher change.13 |
| **Tier 3** | yt-dlp Core | External process resolution with PO-Token plugin | Fork failure or rate limit.11 |
| **Tier 4** | Invidious/Piped | API-based extraction proxying | Instance 500 error or IP block.18 |
| **Tier 5** | Full Download | Background download then play cached file | Disk full or network timeout.28 |

A critical insight from the research is that Invidious and Piped should be used sparingly. While they effectively proxy requests to hide the user's IP from Google, they are prone to instance-wide blocks and often suffer from higher latency due to the double-proxying of the video manifests.18 The most robust systems prioritize local native extraction with a high-quality PO-Token provider.2

## **Playback Integrity: Designing the Professional Media State Machine**

A common issue in media mods is the "False Playing" state, where the UI indicates a track is active, but the audio engine is silent due to a stalled pipe or an unhandled exception. To solve this, the AudioPlayer.java and PlayerFacade.java should implement a state machine based on the standards found in high-end players like ExoPlayer and AWS Step Functions.29

### **Defining Product-Level States**

The transition between states should be governed by actual data availability, not just command invocation.

1. **Idle:** No resources allocated. The initial state or the state after a fatal error.29  
2. **Opening:** The system is actively resolving the stream URL and spawning the FFmpeg process. Metadata is available, but the PCM pipe is empty.29  
3. **Ready:** FFmpeg has started and the internal PCM buffer has reached the "Minimum Playback Threshold" (e.g., 256KB of data). This state represents the point at which sound can be reliably produced.29  
4. **Playing:** The OpenAL source is active, and the playhead is moving. The system is continuously filling buffers from the FFmpeg pipe.29  
5. **Buffering:** The network throughput has dropped below the playback rate. The system pauses the OpenAL source and waits for the buffer to reach the "Ready" threshold again to avoid crackling.4  
6. **Failed:** A non-recoverable error (e.g., video removed, 403 Forbidden after retries).  
7. **Retryable:** A transient error (e.g., DNS timeout, temporary FFmpeg crash). The system should automatically re-attempt the "Opening" state using an exponential backoff with jitter to prevent overloading the service.32

### **Preventing Queue Overlap and Silent Starts**

The transition between two tracks in a queue is the most sensitive moment for playback integrity. To achieve "Gapless" playback, the mod must utilize a dual-source approach in OpenAL. While Track A is finishing, the system should already be in the "Opening" or even "Ready" state for Track B.34

A "Silent Start" occurs when the OpenAL alSourcePlay command is issued before the PCM data has reached the native hardware. By implementing a "Strict Readiness" rule—where the player remains in the **Opening** state until the first 1.5 seconds of audio are fully decoded and buffered—the system ensures that the first note is always audible.23 To prevent overlap, the AudioEngine must verify the completion of Track A's fade-out or termination before the alSourceQueueBuffers call for Track B is made.34

## **FFmpeg as a Universal Decoder: Format Selection and Safe Handling**

The Minecraft mod's current stack uses several specialized libraries like JAAD (AAC) and JLayer (MP3). While lightweight, these libraries often lack the robust error-correction and container-handling capabilities of FFmpeg.17 Normalizing all YouTube formats through a single FFmpeg pipe simplifies the architecture and improves reliability across the wide variety of formats served by Google.

### **Preferring WebM/Opus (ITAG 251\)**

Research into audio codecs indicates that Opus is significantly more efficient than AAC, particularly at the bitrates common on YouTube.14

| Feature | Opus (ITAG 251\) | AAC (ITAG 140\) |
| :---- | :---- | :---- |
| **Max Bitrate** | \~160kbps (VBR) | 128kbps (CBR/VBR) |
| **Transparency** | Perceptually transparent for most listeners | Transparent for speech; artifact-prone for high-freq music.14 |
| **Latency** | \< 26.5ms (Algorithmic) | \~100-200ms.14 |
| **Recovery** | Excellent packet loss concealment | Prone to "chirp" artifacts on loss.14 |

For the YouTubeStreamResolver.java, the system should explicitly prioritize ITAG 251\. Not only is the quality higher, but the WebM container is more easily parsed by FFmpeg's low\_delay flags than the M4A container, which often requires more "moov" atom analysis, leading to higher startup latency.23

### **The Normalization Pipeline**

To avoid issues with "mixed container formats" or "bad metadata," the system should use FFmpeg to normalize all inputs into a raw PCM stream. This stream is then fed into the AudioEngine. This approach bypasses the limitations of the Java Sound API and provides a unified interface for the AudioPlayer.17

**Recommended FFmpeg Command Template:**

ffmpeg \-loglevel quiet \-probesize 32k \-analyzeduration 0 \-i \-f s16le \-acodec pcm\_s16le \-ar 48000 \-ac 2 pipe:1

This command forces a 48kHz sample rate (the professional standard and native rate for Opus) and 16-bit stereo output, which matches the expected input for OpenAL sources in Minecraft.4 The use of pipe:1 allows the Java mod to read the raw bytes from the InputStream of the FFmpeg process, providing a direct, high-performance bridge between the network and the game’s audio hardware.17

## **Prefetching, Caching, and Resource Management**

A professional streaming mod must be a "good citizen" of the system, avoiding redundant downloads and minimizing background contention with the game's primary rendering and networking threads.

### **Pre-resolution and Metadata Caching**

The search and discovery logic in YouTubeService.java should be decoupled from stream resolution. When a user searches for a track, the mod should fetch and cache the basic metadata (ID, title, author, duration) using the InnerTube /api/v1/search endpoint or NewPipeExtractor. This is a low-bandwidth operation that does not trigger aggressive rate limits.3

**Prefetching Rules:**

* **Active Search:** Fetch metadata for top 10 results. Do NOT resolve stream URLs.42  
* **Queue Management:** Resolve the stream URL for the *next* item in the queue when the current track has 30 seconds remaining. This allows for the "Opening" and "Ready" states to be achieved before the current track ends.34  
* **Caching Policy:** Files smaller than a certain threshold (e.g., 20MB) should be automatically promoted from the stream-buffer to the YouTubeDownloadManager cache after the first successful play. This ensures that a user's favorite tracks never require re-resolution or network bandwidth after the initial listen.15

### **Avoiding Network Contention**

Minecraft is a network-heavy game. Large entity counts or complex chunk loading can saturate the client's network thread.43 Modern performance forks like "Pulse" for Paper and similar client-side optimizations use Netty flush batching to reduce CPU overhead.43 The AudioEngine.java should utilize its own dedicated thread pool for FFmpeg I/O to ensure that media streaming does not stall the Netty pipeline used for game packets, which could lead to player "rubber-banding" or disconnects.15

## **Final Design Recommendations for a YouTube-Native Pipeline**

The research concludes that the most effective way to ensure high-coverage, low-latency streaming in Minecraft is through a tiered hybrid architecture that leverages native Java extraction and optimized native decoding.

### **The Recommended Extraction Stack and Fallback Order**

To achieve near-universal coverage, the mod must move away from a single-source resolver.

1. **Primary:** NewPipeExtractor (Java-native) for its lightweight footprint and excellent handling of YouTube's signature challenges.13  
2. **PO-Token Provider:** A mandatory sidecar or integrated service (e.g., bgutil-pot) to provide mweb-client compatible PO-Tokens.2  
3. **Secondary Fallback:** yt-dlp using an external process, which remains the "gold standard" for adapting to major YouTube UI rewrites.16  
4. **Tertiary Fallback:** An Invidious instance API (e.g., yewtu.be) for scenarios where the client's IP is temporarily flagged.18

### **Buffer and Startup Policy**

To meet the 2-10 second latency target while maintaining reliability:

* **Startup Threshold:** Buffer at least 1.5 seconds of PCM data (256KB) before transitioning to **Playing**.36  
* **FFmpeg Optimization:** Strictly use \-probesize 32k and \-analyzeduration 0 to bypass metadata analysis.21  
* **OpenAL Configuration:** Use 4-6 periods of 20-30ms each to provide a buffer of 80-180ms against JVM GC pauses.4

### **The "Product-Level" Rule for Player States**

The PlayerFacade.java should govern transitions based on these criteria:

* **Ready:** Stream URL resolved \+ FFmpeg header parsed \+ 256KB PCM in memory.29  
* **Buffering:** Active playback interrupted \+ PCM buffer \< 64KB. OpenAL source must pause to prevent crackling.4  
* **Failed:** Resolution failed after 3 retries OR FFmpeg returned a non-zero exit code (excluding SIGTERM).31  
* **Retryable:** HTTP 429 (Too Many Requests), 503 (Service Unavailable), or transient socket timeout. Use exponential backoff: 2s, 4s, 8s.32

### **Techniques to Reject as Fragile**

Based on the investigation, several common practices should be avoided:

* **Raw Java Sound:** Standard Java Sound SourceDataLine implementations are often poorly supported on modded clients; OpenAL is the only safe native path for Minecraft.4  
* **Public Invidious Instances for Primary Playback:** These instances are frequently rate-limited and should only be used as a last-resort fallback.18  
* **Unsafe Seeking on Streams:** Seeking should be disabled or limited to "local-only" (cached) tracks to avoid triggering 403 blocks from Google's CDN.15  
* **Redundant Downloading:** Never initiate a full file download if the user only intends to stream; this causes needless background contention and disk wear.5

By implementing these architectural principles, the Minecraft Fabric mod can provide a professional-grade audio experience that is indistinguishable from native game sounds, offering users a stable, fast, and highly reliable gateway to the vast library of content available on YouTube.

#### **Works cited**

1. Extractors · yt-dlp/yt-dlp Wiki · GitHub \- YouTube, accessed April 27, 2026, [https://github.com/yt-dlp/yt-dlp/wiki/Extractors/0f7ec7f59ae13957f2a4c60c171597b0f361a1c4](https://github.com/yt-dlp/yt-dlp/wiki/Extractors/0f7ec7f59ae13957f2a4c60c171597b0f361a1c4)  
2. PO Token Guide · yt-dlp/yt-dlp Wiki · GitHub, accessed April 27, 2026, [https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide](https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide)  
3. YouTube Scraper — Videos, Channels, Comments & Search \- Apify, accessed April 27, 2026, [https://apify.com/automation-lab/youtube-scraper](https://apify.com/automation-lab/youtube-scraper)  
4. extreme crackling sound in OpenAL applications caused by period count \= 3 \#763 \- GitHub, accessed April 27, 2026, [https://github.com/kcat/openal-soft/issues/763](https://github.com/kcat/openal-soft/issues/763)  
5. Trickle: Rate Limiting YouTube Video Streaming \- Google for Developers, accessed April 27, 2026, [https://developers.google.com/speed/protocols/trickle-tech-report.pdf](https://developers.google.com/speed/protocols/trickle-tech-report.pdf)  
6. Trickle: Rate Limiting YouTube Video Streaming \- People, accessed April 27, 2026, [https://people.csail.mit.edu/ghobadi/papers/trickle\_atc\_2012.pdf](https://people.csail.mit.edu/ghobadi/papers/trickle_atc_2012.pdf)  
7. Video playback issues: How to identify, test, and fix them \- FastPix, accessed April 27, 2026, [https://www.fastpix.io/blog/video-playback-issues-how-to-identify-test-and-fix-streaming-problems](https://www.fastpix.io/blog/video-playback-issues-how-to-identify-test-and-fix-streaming-problems)  
8. How to Fix YouTube Live Stream Video Playback Interruption \- OBSBOT, accessed April 27, 2026, [https://www.obsbot.com/blog/youtube/youtube-live-stream-video-playback-interruption](https://www.obsbot.com/blog/youtube/youtube-live-stream-video-playback-interruption)  
9. Best YouTube Scraping API in 2025 \- SociaVault, accessed April 27, 2026, [https://sociavault.com/best/youtube-scraping-api](https://sociavault.com/best/youtube-scraping-api)  
10. How to Scrape YouTube: A Complete Guide to Videos, Comments, and Transcripts (2026), accessed April 27, 2026, [https://liveproxies.io/blog/how-to-scrape-youtube](https://liveproxies.io/blog/how-to-scrape-youtube)  
11. bgutil-ytdlp-pot-provider \- crates.io: Rust Package Registry, accessed April 27, 2026, [https://crates.io/crates/bgutil-ytdlp-pot-provider](https://crates.io/crates/bgutil-ytdlp-pot-provider)  
12. rustypipe\_botguard \- Rust \- Docs.rs, accessed April 27, 2026, [https://docs.rs/rustypipe-botguard](https://docs.rs/rustypipe-botguard)  
13. TeamNewPipe/NewPipeExtractor: NewPipe's core library ... \- GitHub, accessed April 27, 2026, [https://github.com/TeamNewPipe/NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)  
14. Best Audio Codec for Online Video Streaming in 2026, accessed April 27, 2026, [https://antmedia.io/best-audio-codec/](https://antmedia.io/best-audio-codec/)  
15. lavalink-devs/lavaplayer: Lavaplayer fork maintained by ... \- GitHub, accessed April 27, 2026, [https://github.com/lavalink-devs/lavaplayer](https://github.com/lavalink-devs/lavaplayer)  
16. Top 10 Free Working yt-dlp Alternatives 2026, accessed April 27, 2026, [https://www.winxdvd.com/streaming-video/yt-dlp-alternatives.htm](https://www.winxdvd.com/streaming-video/yt-dlp-alternatives.htm)  
17. \[Feature Request\] Support youtube-dl \+ ffmpeg as an alternative to lavaplayer · Issue \#1375 · jagrosh/MusicBot \- GitHub, accessed April 27, 2026, [https://github.com/jagrosh/MusicBot/issues/1375](https://github.com/jagrosh/MusicBot/issues/1375)  
18. Invidious API \- FreeTube Docs, accessed April 27, 2026, [https://docs.freetubeapp.io/usage/invidious-api/](https://docs.freetubeapp.io/usage/invidious-api/)  
19. Invidious, Piped, etc... Nothing works. There's no more reliable private frontends for YouTube? : r/degoogle \- Reddit, accessed April 27, 2026, [https://www.reddit.com/r/degoogle/comments/1rzm6nq/invidious\_piped\_etc\_nothing\_works\_theres\_no\_more/](https://www.reddit.com/r/degoogle/comments/1rzm6nq/invidious_piped_etc_nothing_works_theres_no_more/)  
20. What does it offer over NewPipe, if anyone knows? \- Hacker News, accessed April 27, 2026, [https://news.ycombinator.com/item?id=36144730](https://news.ycombinator.com/item?id=36144730)  
21. How to minimize the delay in a live streaming with ffmpeg \- Stack Overflow, accessed April 27, 2026, [https://stackoverflow.com/questions/16658873/how-to-minimize-the-delay-in-a-live-streaming-with-ffmpeg](https://stackoverflow.com/questions/16658873/how-to-minimize-the-delay-in-a-live-streaming-with-ffmpeg)  
22. FFmpeg Formats Documentation, accessed April 27, 2026, [https://ffmpeg.org/ffmpeg-formats.html](https://ffmpeg.org/ffmpeg-formats.html)  
23. How to Minimize Startup Latency in DASH Livestream (\~1.5 seconds)? \- Super User, accessed April 27, 2026, [https://superuser.com/questions/1864976/how-to-minimize-startup-latency-in-dash-livestream-1-5-seconds](https://superuser.com/questions/1864976/how-to-minimize-startup-latency-in-dash-livestream-1-5-seconds)  
24. Stuttering sound and performance loss when using OpenAL HRTF \- The Dark Mod Forums, accessed April 27, 2026, [https://forums.thedarkmod.com/index.php?/topic/20466-stuttering-sound-and-performance-loss-when-using-openal-hrtf/](https://forums.thedarkmod.com/index.php?/topic/20466-stuttering-sound-and-performance-loss-when-using-openal-hrtf/)  
25. How long the URL of a YouTube video last before expiring? \- Super User, accessed April 27, 2026, [https://superuser.com/questions/1823181/how-long-the-url-of-a-youtube-video-last-before-expiring](https://superuser.com/questions/1823181/how-long-the-url-of-a-youtube-video-last-before-expiring)  
26. Why my YouTube Live Playback URL changing? \- 5centsCDN Help Center, accessed April 27, 2026, [https://www.5centscdn.net/help/why-my-youtube-live-playback-url-changing/](https://www.5centscdn.net/help/why-my-youtube-live-playback-url-changing/)  
27. Extractors · yt-dlp/yt-dlp Wiki \- GitHub, accessed April 27, 2026, [https://github.com/yt-dlp/yt-dlp/wiki/extractors](https://github.com/yt-dlp/yt-dlp/wiki/extractors)  
28. Repeat — LAVA 2026.01 documentation \- lavasoftware.org \- docs, accessed April 27, 2026, [https://docs.lavasoftware.org/lava/actions-repeats.html](https://docs.lavasoftware.org/lava/actions-repeats.html)  
29. Playback Reference \- Castlabs, accessed April 27, 2026, [https://players.castlabs.com/apple/latest/docs/playback.html](https://players.castlabs.com/apple/latest/docs/playback.html)  
30. Player events | Android media, accessed April 27, 2026, [https://developer.android.com/media/media3/exoplayer/listening-to-player-events](https://developer.android.com/media/media3/exoplayer/listening-to-player-events)  
31. Handling Errors, Retries, and adding Alerting to Step Function State Machine Executions, accessed April 27, 2026, [https://aws.amazon.com/blogs/developer/handling-errors-retries-and-adding-alerting-to-step-function-state-machine-executions/](https://aws.amazon.com/blogs/developer/handling-errors-retries-and-adding-alerting-to-step-function-state-machine-executions/)  
32. Handling errors in Step Functions workflows \- AWS Documentation, accessed April 27, 2026, [https://docs.aws.amazon.com/step-functions/latest/dg/concepts-error-handling.html](https://docs.aws.amazon.com/step-functions/latest/dg/concepts-error-handling.html)  
33. Enhanced Error Handling for Step Functions Provides Developers with Fine-Grained Control over Retry \- InfoQ, accessed April 27, 2026, [https://www.infoq.com/news/2023/09/step-functions-retry-control/](https://www.infoq.com/news/2023/09/step-functions-retry-control/)  
34. Best Way To Create A Gapless Album? : r/Logic\_Studio \- Reddit, accessed April 27, 2026, [https://www.reddit.com/r/Logic\_Studio/comments/ad6xtv/best\_way\_to\_create\_a\_gapless\_album/](https://www.reddit.com/r/Logic_Studio/comments/ad6xtv/best_way_to_create_a_gapless_album/)  
35. Gapless Album Tips : r/LogicPro \- Reddit, accessed April 27, 2026, [https://www.reddit.com/r/LogicPro/comments/109p2rh/gapless\_album\_tips/](https://www.reddit.com/r/LogicPro/comments/109p2rh/gapless_album_tips/)  
36. openAL \- choppy sound when playing the buffers \- Stack Overflow, accessed April 27, 2026, [https://stackoverflow.com/questions/5899149/openal-choppy-sound-when-playing-the-buffers](https://stackoverflow.com/questions/5899149/openal-choppy-sound-when-playing-the-buffers)  
37. What is the difference between the ffmpeg vs lavaplayer on discord bot? \- Stack Overflow, accessed April 27, 2026, [https://stackoverflow.com/questions/70764961/what-is-the-difference-between-the-ffmpeg-vs-lavaplayer-on-discord-bot](https://stackoverflow.com/questions/70764961/what-is-the-difference-between-the-ffmpeg-vs-lavaplayer-on-discord-bot)  
38. opus vs m4a : r/NewPipe \- Reddit, accessed April 27, 2026, [https://www.reddit.com/r/NewPipe/comments/nkhvpg/opus\_vs\_m4a/](https://www.reddit.com/r/NewPipe/comments/nkhvpg/opus_vs_m4a/)  
39. Robust Audio Streaming Over IP \- Internet Society, accessed April 27, 2026, [https://www.internetsociety.org/inet99/proceedings/4p/4p\_3.htm](https://www.internetsociety.org/inet99/proceedings/4p/4p_3.htm)  
40. Is there a way to reduce the initial startup time for FFmpeg when muxing subtitles?, accessed April 27, 2026, [https://superuser.com/questions/1839735/is-there-a-way-to-reduce-the-initial-startup-time-for-ffmpeg-when-muxing-subtitl](https://superuser.com/questions/1839735/is-there-a-way-to-reduce-the-initial-startup-time-for-ffmpeg-when-muxing-subtitl)  
41. FFMpeg Low Latency DASH \- Tebi.io \- Documentation, accessed April 27, 2026, [https://docs.tebi.io/streaming/ffmpeg\_ll\_dash.html](https://docs.tebi.io/streaming/ffmpeg_ll_dash.html)  
42. Best practices for handling third-party API rate limits and throttling? \- Reddit, accessed April 27, 2026, [https://www.reddit.com/r/node/comments/1hsrlrf/best\_practices\_for\_handling\_thirdparty\_api\_rate/](https://www.reddit.com/r/node/comments/1hsrlrf/best_practices_for_handling_thirdparty_api_rate/)  
43. I rewrote Minecraft's network flushing in a Paper fork. Here is how I dropped PPS by 97% (16k \-\> 170 PPS) : r/admincraft \- Reddit, accessed April 27, 2026, [https://www.reddit.com/r/admincraft/comments/1qk20r7/i\_rewrote\_minecrafts\_network\_flushing\_in\_a\_paper/](https://www.reddit.com/r/admincraft/comments/1qk20r7/i_rewrote_minecrafts_network_flushing_in_a_paper/)  
44. youtube — Platypush Documentation, accessed April 27, 2026, [https://docs.platypush.tech/platypush/plugins/youtube.html](https://docs.platypush.tech/platypush/plugins/youtube.html)  
45. Invidious Instances, accessed April 27, 2026, [https://docs.invidious.io/instances/](https://docs.invidious.io/instances/)  
46. Manage live stream settings \- YouTube Help, accessed April 27, 2026, [https://support.google.com/youtube/answer/9854503?hl=en](https://support.google.com/youtube/answer/9854503?hl=en)

[image1]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADkAAAAYCAYAAABA6FUWAAAC50lEQVR4Xu2XS8hNURTHlzzyTB555K2QFCEpz+sVBgw8yhelKK+JRyFGSmJCkQxQQpJHSd4UXxlQZKpISRmYGIiRwv/3rX26275d373385Vz3X/9uvesfc4+e6+91tr7mDXUUEP/ouaLd+JDhSz0x/KjDuKkuCJGhmt0VvwQS8J1RzFXvBfTgi03GiiuigGRrY94YT6hIZG9p7gohka2XIjQ25nYJokv4rroFNmZ/AnRK7LlQqvF2MS2VvwUexN7P7HJiiGda5GP38WstKFeVC4f60pTxTcrzcdK1EV0To01qLt5NW83lcvH1oRDLotlaUOV6i3umzu7XURBOWe15SNb0TMxPm2oUlT2p6J/2vC3VGk+zhDXxBnzPXOjuCE+mTtpYrivm9hsvg+vsOLqsNrYZooj4qD5u3eLh+anquNimJgiboqlLU/6StMnezb/j4n9omC+h2+1VlKmknycIE6Ztx8VG4KdF7OHZmIQt81PTETIJXHI3Hk4hYEx0cXmR8oR/lhLH/SFOKBsESvNT2TkKfs6Du1q7rjZ4m24h/dcEMt5OBZ75Cvx2TwXM76K11aaG7yENjormHuNCeMYXpppjbhrvpoM6Jb5avQVw8M1g+F5VgSxmo+teGzk3kHm0UGtQDgH0Bgxz/w9ODUbR7X1pEQMqkk0i49inHn+PLHf8/G8FQfD6pFno8tcZ+J5+onzMb4XZ7GK8QcCE8oiiHvfWBs/IArmnQw2P9pRTUeZF6kHwbYu2JhkVmkJ2XtigXkfDILrHqE9E6tFWOPIbeYrTCQR9vRN/j8X08V6K40gfu+Yr2rNwtN4bZV5YWBQ5AF52iz2WfGrhVyjEOwQp823hcPmebZLHAj3xVokHplHwORgY6Lk7nbz51+GX6owK0dKUZjIXQoh4d1mkfycYdPNmlCCWKxItlrxQeFPhwbuT9t4F/mKQ+M+iSDyEUfk7qOhEjFhIodKX7dir6aC7xFzkrb/T78A3c+E5Qm1nOoAAAAASUVORK5CYII=>

[image2]: <data:image/png;base64,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>

[image3]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADYAAAAYCAYAAACx4w6bAAAC7UlEQVR4Xu2WWehNURTGPxkyi2QomcosFB7MZQpFQoZ4UEIUkiJeDPEkEfIgkgdjhDIrU8KTBxEPFBJJ8iKlTN/3X3s552z/O/T/S/fW+erXvXfvffdZ+6xvrXOAXLly/U+NJG/IrxRfyLvw/Qe5RHr7H6pNe8lXMjwa70VekmekSzRX8WpJbpOnpH12qkZHYdmbHk9UunqS9+QIaRDN+aG/kRHZqcrXDFhGlscT1DxYne0njaK5itcOWEamks6BbmQz+UAWkIZ/VleJ3GofYVY8GDgMq7ntpLUvriYVq6/usI74iHRIjbchK8JnOWpMliLbVZuE8bQmBIpJ5bCTfCezormMitWX5B1RNnUNhtWcsl2OdKBDMItLCu4Esl1WYyqJSamxQlLnvkX6xRNpFXp+Sc3IVfKTTIzm6qOO5AFKBFZEo8kd0jaecJV6fo2BNZXrsLWy6kxymQwJa3qQ42QOGUVOkT1IbKogLiDJ+BJyDtaUZP9BpBM5QLYha8/+Yc1WMp60C+NylxJSULpjn8gxZOtLHVCBfCZPYB1S0nqNbyJbYP9ZRSaTF0iec7KvbKa6XERmww7snTUdmPZYDLP3fSTXUomchd1Quek5rB/IsmfIwrAuI2XiNZJ3Qz2n3sLeGfWpwnwFC1p2dCnQrrCMKRO6iF65FKg3HwWiDMu6usPKhuY8EA/MC9/30EFOh9/K9l0yJazRXldIC5RZX3WRsnIDidV0mHTgA2H+d2vLqvfCp1RbYDqM7Dw//B5KHiPpohtgjUUqWV91lSykC02DHUIX0EEVjORBqG2PRZJN3ZBxsMCukVYwm+rAOuRD2H7LyDByHpZ9OUYOUUZXktWwGFQS/zRru8g+mE2VLdWGLuwZXEd2kzWwRqBmcZJshAU5ANaw9Nut1ofchDWPvrAD6SVhLWyvi7DmoozOhWV3Pf5+DtZLOozutjcbNYTmyXSNVAvpZqT16VeypoG0ahvz/2kv7RmP58pVifoNHaWKhv5YL5gAAAAASUVORK5CYII=>

[image4]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAC8AAAAYCAYAAABqWKS5AAAChElEQVR4Xu2WzauNURTGH6EQSeSjTBiQKCTluysM5FK+iiRFiIkihInIgDJggFBCl3xlYGCguD4if4CUMjCQUjLBwEc8v9Z+8959zj0D99x6y3nq1zln7f2+Z6211157Sy219H9roflgfpf4aL6bX+alWW36Fg9UURfNDzOvZMPhbYog9pk+pbHKaIh5at6aUdnYGPOum7FKaJL5ZG6bftnYTPPNvDIjsrFKaIWi1rfnA9ZhxdiezF4ZnVZtvfc3WxQrsjf9rpwGm05Fd3mevr9WZPusGV5MrKLq1Ttd5YCiyyxJtkJHzE/VL7Fmik43KDfmKup9d2afYb4qWmhZw8xjdS2x3tA6cyk35qpX72iDIqhjmX2qoq32duchaQ1Xt1F/Jyic35/ZCeqJOWFOmtFmoDloLpihad4is0BRihx058xcc97sTHY0x9xSPDvWTFNknFP/ntmU5tVoivms2v7O95vq6vwhs1gRFA7QfVYp/mitmWXum9lmgLmb5k837abDHDVbFY2BICebM4r/IxGbFWIfPlI3qztfcWrm9xnqvxD3GTYsQWxUZIbTtlzvrEKnopRw/oFiT5BBVpTPkWaCeabYRwRGh0ME98VcMW362455L8H26DpCKS1XdBxKg4y8SHbEKgBihYr9gVNkHkcRTheBlYWz6xUJeG8mJjvvbFjv/yIyjFNkjgCofcoCXVaUEaL+CWSlGa9wJO9abeaNYjXZe9fNOEWADxXXEt69LM3vscjUcbPLXDVLS2OU2TWzw9xRbELmUc/sEVpfWawiGV5jTilKhTJhhW8oriW076ae7PwB2al3v2dFilIhm8UcDpt687Fxgudj/Ob5llpqhv4AKhV7YHK00aMAAAAASUVORK5CYII=>

[image5]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAmwAAAA0CAYAAAA312SWAAAMDElEQVR4Xu3dCaxn1xzA8Z9YYt9GlVCdqX1pamlRsZRUg4YIaqcTUhppFM2g1lGkwSSWbiKlIWlstTSWqoqZliA0trSaEGmJJUhJBAliOd85/zP/8z/v3P//zrz/e503/X6SX+a9+1/uufee936//znnvomQJEmSJEmSJEmSJEmSJEmSJEmSJEmSJEmSJEmSJEmSJEmSJEmSJEmSJEmSJEmSJEmSJEmSJEmSJEmSJEmSJEmS5rhNipu0GyVJkg4kN09x18m/G83BKT6Y4lbtA5Kk/ROfsF+T4uT2geT2KU5N8ZEUb01xyOzDo7TvcffZh3ejDY9K8aEU56Y4fvbh3e6f4r2R3+eFsf6J5qaR20X7iPXef338p0X/PK63h6U4rt040V7Tx0+21ZZ1TPSxef2La0Wf4Tnsj/321O2Zd30Zmbo4xf9S/D3FI2Yf3hCen+JV7UZJ0v6LpErSeUOz/dAUl6Z4SuQkeEyKn6R4VvWcRXgPXnNSilumeGqKn6d4ZPUckvjrU1yeYkuKTSkujNlRi2en+FmKh6a4bYp3prgsxR2q56wl2nJWirenuHfk47kq8vGtB47/cymOSPG4FD9K8a/Yu2uxLPQXrtcPIhcsbb8B52t7iu+nODzFAyNf33fEtGhb1jGVPjbUv+gj9BX6DH2HPkRfYv+1to+N6V+0dZkF2x1TvDzysaylm0Xuz1wXSdIGQEL6ZqxMvCTVs2Nl8qR4uyIWJzKQFM5PcdHk6+LdKb4W0xEMkt0fUjx2zzMiDou8LzCq94sUL5o+HHeKXDCcUm1bS7TlGynuUW3jnHF89bGtBaauvpXi2JgWO/dJ8bvI52VfRj1Xg4Lt6SmeFv1CH/Qbiq9yDcH1vT7F0ZPvl3FMdR8r2v5F++gr9JmCvnRN5HOLXh8b0784D8ss2CigPhu5YFxL7Oe8WPu+K0laAhLl61K8L1YmXhLGrhRbq20gMV0Z00Q3D0XX72NlQm9HJUiwv4rZqazbpbggchtJom1SZDujcLtifnLjeZsn//Ywmsf01iIcQynQit+k+GXMPxeL9o9FbeC4Of56X+X4aROjSjeE0q72+jI69JXJY/U14/pynZkixTKOqdfH6v5VCvuPV4/jqBR/S/GMyfe9Pjamfy27YHtxip0xf5/LwFQoU6KSpA2AkZIdkUc82sTLJ29GLRgl2RZ5iouEenqM/2TO6Ml/Y2VCJ8mRlEmSJbm3BRsJq4yKkOB7SZEkTLImaQ+hnW9K8dr2gcijDOx7zLTmkSl+nOIV1Tba3La7Ve+/V7SNaQOjmUwdUsDWiZzj5zxyPm8IQwVbKfbba1YKNh7jOcs4pl4fq/sX55dRvbZgK23nwwJ6fWxM/6oLNgpPvmdNX12A08c5doLinH5Qb+OmBdZHUkT+OsV3I4828lg7Ncp2pqPbtWf8fLJG8LTIxS7TzEPtZuSRDx5bmu28jtfzPrTloOox2sx7skbwxOj3edrwpMjnlGldSdKSsLB6cwwnXtYB/SVy8mMt0vtTfCHGTYeiJM72fevtJbm3hQ/byzYSZ5tMMbS9RSJhFLEumMYUSov8O/L5aJNqq+x/WyyvDWXkiKnkoQX0a22o3ywq2IZGJfflmHp9rN5W2jhUsJXtvb7U29ZiXxSMrL9jjdxzUpwZuUg8fvIc1sSx7OA/kT/s0F9Ys/ePyO28ZPI9xeufJsHX/HzyWtCH3hL5/NAeppopruhPFHy8P6OFnGMeo/AbKnr5gMaNFW1ffHTk17808uvLcVN80h76Os/jca5RvVyC7az/o833TfHqmI5eSpJWgV/WJ0y+Hkq8eGKKf0ZOLMTbYv7dczXer02mqBNqSeJDBRsjCruinzjHJNSChFcKptUUSgXvRzFbL26fpy3aVtuG50UuABhtqRNvjXPH9DXJd2y8YPcrx5nXb7ZHLkiOqraxho0it73WxZhjavX6WN2/ytelMCvqgm2owBzTv8r7b222s2/6B3fRguNhCn3Lnmfkc8QxF6UdRDslSnH015iu/0MpbBkZuzzyMoLilTFcsL05ZtcWgqUR9Tk/I6bHzbHUbed5rG/lRo9NkT/AXRGz6wYZYXOUTZJWiWTw4er7XuLlU/sPY/ZT9IMijyQwotD+wu/pjX6029djhK1gWpNF7YxUjC0IeijSODf7YrVtIDlS6J0aeRrthtTrN7XNkW/U+G3kfvOSmJ0SLVZzTL0+tt4jbOyrLY7K+18U0+UD34vpsfPzQ+FUGyrYyoeaeuStBCNwfBjYEdMPVcSnoj8SzhTy9nZj5OfWr39P5Pctx3FtzO6X4G+4bY5p0dyeA0nSKh2e4qcxHVX5Y+RfuCzC5nt+qfOpnT9bcZfJa4qDI99dN5Ska2VEpX1uSXIUgyQzklqvYOMuQkYNWBPTS5wkVD7537PZPuTSyNM1jBJui30rmCjWvh159Iq2s0icP8UwBsl1NW0ohQ2jMuV1rDcq02Ytih8Kb87r2GhHduZZVLC1GFW8PqY3HWBvj6nV62N1/zos8jq0oYKN9YXo9bEx/WtRwXZhTI/rlMij1Ux/npPiIZPtRVuw3TnFwyP/DF4dK+90bfGzeULku0xp0wditn/xNVOhR1fbapsjr0/bGflD2ckxvWZ14dmyYJOkddJLvPzy7SUIfumThJhyKShYekULfwLjuphN0OC1JAGSAdhv/T1IUuV1rIVhqoxCsig3KxCL1pCBAuDQydeMHOxLwcTrSVzlfWgjIw0UlYuUYm1f28DzScD1iCfeFbPTjjXWHrGOinVVY6O+Bov0+k1xROSbNJ5ZbeM6Mk1aCoaxxzTUv9DrY3X/KkVQ20/oS9xQU/pUr4+N6V9DBVspJCnSii2RC0DWm300Vr5vW7Bxfs+K6RRkewPE/VLcK/K+62vA81lPtytmC3DO1QXNtoJ+WHBdPhO5YGWKk6nO3u8CrvFBka8nhShFb43HJUlLRHIkkZbRBjCqwBoVRj5qD4h8FxvJB6yhYS0NiahVEgdTQWV6piSDT8b0EzujVby+/jMDjEKUxL4p8k0P2/c82n/NEIqlLzfb9rZgulvkdTp/jtmRSY5laOShKPsvxVoxtg08b3vkpFivN+P4r4vhuwHXWjtKVSuFzPbJ91x/pkfPi+n54rFFx1T3r9LnanUfQ69/MQrKe5fX9/plr4+N6V/lOE+P6TWkDRRGV0Ue4SxK4cXze+9Le/lAUIojir43Th57cOQp0TNjOm3MlCQ/B7SBtnMMBYUibaj71bz/2YCCj3ajtLMUgU+OXHxunTwG9nXu5F9exwcXpvr5/VDUH+okSatAMvlO5JEFkghBEXLs5HEKJj7VfyLyL3vWtVwT+db9ohR2TKH0kBC/muLTkadZPxZ5SrGdZmKU5drIfzbjxMh3nNXJ5sjJ4yxIZySIpFbW2cxz68iL/dtiCSQ+7mZ7TPtAR5n2aaMdVWjN2z/GtKEURu2+id7Ix1pjqozkXLeDQpYp7FKgcH2vjFwYsficry+I2T93MeaY6v7VjmIVpY8N9S/6CIXizsgjfrTp6lg57dr2sTH9izZRZL0s8mgchQvrG3el2LznWVOsXeN4mL7soQ38DF6S4osx228YUaMwoxBkPxwvaANrBNkvI3dMiX49Zs8BxeA5sXIatuBDAwUs73tx5FH0eg3cEyL/YWGuMfu4LGbPH9eVUU5ujOBn/fOx+NxJkpaIX7qsKSKB8e/QL+GT2g0VihKKDt6Df8sIQYtP6yQfoh4tKEgKx0VOuoc0j2n/w5TfMbGc60X/KgVKD31qXv+i+KfgWdSP6z62t3hPCrGh6VtQsLU3G7R4HwrfoTby/nXBd4vIz+W4eV1v/4xYMr1aRh1bfLAo7e9NmYJzyM8l++idY3DN93YtpCRpnZAEdrQbpSUp/as3JboRMNL1pVg8yrWWKHh707CSpBsRRg2YPpLWQulf89b57c9YYsCULksJKDyHRrnWCjcOnB8bt+CVJC0Byee50f+bT9JqHQj9i6nGMyIXa/UavvXCiB7736gFryRJ0gGPNXncNCBJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJkiRJNyr/BwtItcbmxBtJAAAAAElFTkSuQmCC>

[image6]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMYAAAAXCAYAAABQ+TDXAAAG1ElEQVR4Xu2ad6gdRRSHj6hg1NixoJIXCUaxYwkRS8CgBlHEqAgKBiU2gi0YCwiKiKiJWFBExIpYEBTsBU1UrGADFSwYRQ0KURT1D8Vyvnd2srNzZ/eeu+++R8LbD37ct7O7s2fOzJkzO/tEOjo6Ojo6Ojo6Ojo6xpcR1SlpYcRM1Q2qu1TzVetXT4+yg+pKsWuuVs2onnazmeoCsXqoj3pT1lPNUt2qukN1jORt8tQ1EXjtzTEsvw6L2KfYs6tY+2LS9h6r2rByxeBQ54Wqc9IT4uvn1KbaPthddZ7qFdU/qvurp0ehsgWqz1WHqqapHlI9oJpSXiYHqV4qrtlH9YzqP9Vi6XVaE9T/kWqhaiPVPLFnU3+A+paoVqimq7YWswmnxM731DUReO3NMSy/DgtsWK6ao9pSdbbqL6naw+8lqtvF+gDdqHpWtXlxTRsY1H+oLk3KPf08UB8QGMerDlZ9J/nA2F/MmEVR2S6qH6TMMATIk6ozpIxAHvyu2L3U4WED1d2qx4u/A9eqnpcyEKnvR9Uha64wm75RHV0ce+uaCDz25hiWX4cJsy2T6HHFMcHxnmq12HiCPVVPqKYWx0BbCIymVUkTBBQTOJNCHBjefm7VB6QdLsgFBo7AGFJhYFOxWYPZiwgN9/8mNqMErhC79+KorAkMXSW9M8IJUh0INJrnxemSTnhdda/Y7OCtq46dJTOTRGwiNkg9eOzNMSy/DpNlYs8+szgO7YhtZKy8L73+YXy1sRn/cB9ZJ80Y3n5u1QdNgUFZXWCEBzGAblG9UBwHMDaN8Cbmqv6V3ut5NvWcKhaIBGTayGATsxezmKeuJk5T3Sz54NhebEb0LMm89uYYll+HCTZtI2UGIzv8ItYW2gTMyn+LZTbeP2BE9Y5qv+J4EFhCLVXNlt7A8PRz6z5oGxjpTBZDWiO9kXbnVE/VEhpT10jK06AMpOWeuppgBlmkuk2qwTFIUEBqV7/yfrTx63jBCy/r9G9V+0bl+Iu1O37m/eMmsZmZWXxQWEJR14iUy/q47+r6s82Y6aEpMFgT8oC4USF9NS1JiHLOZ19uaqibCeNGBlvTxqSN9NTVjzQ4Bg0K8NrrxevXrcTW2Axar9jN8cAy8hGxe75SHSW9uzsbqx4V8zX6TLV35Yr+4P+LVCcVx7nA8PRz6z5oCgwi9mWxdRgdgbGXiaWvusAI9zwo5kQvrCP7NXI7sc5IG5M20lOXhxAcj6meksGCArz2emjr1/FkN7GNmNgmxsnSooyNnQ/EfM54iV9++4Gvl0k5AeQCw9PPrfugKTCA2YeU+b3YNti5qtckXyGNuFMsfQ6681M3aNukRU9dXngR/1Bsi7ppls6R2tWvvI6x+HU8YeJgbODT8H2B31fF2gjYy2TK8s+7I8i9tHckKssFRl1/thkzPfQLjBRevj6RclcqEDrvcilTK1t4R665opnw0lbXSJZzYY2dNiY0krXsVPHV5WEnsc6cJfY9J33n6IfX3iba+JUBy64Qz/Rqi9E768EOljYo9gE+xqeMH7IGWY3vGylk3tQPdeyl+liqS72fxJ7ze3HMi7enn1v3ARfXBQYDY4XYF0ecDUQuL97xnjTnFos5LVwHOCgehDi/rgN2VK0U2yKOoY7VUu6T44T4GEKwhnu9dTURgiIsn2jXAhk8ODz2AnVOK34DXr+mUMcRqhMHEMHfRJix0yU044ZBSFvCYMvt+h2oekus7YCN2xa/HnIZw9vP3j6oEAKDlBg7H4IxD4tFHo3gxe9FKVNlGDB/in0ojKP8ZynXlTPFPrJwzfSiLIZ6rlG9LeUXUp7H+j48H2aI1REHJl+GmVFmF8feuuogKJ5WHZCUtwkOj72wTGyAXVUce/06UbCk/EJ1n5Q+JSuxLcuWbdiKJZCXR9cAme46KdsGaXv7QWDhC77jBLz97O2DUeaKXczaDwNDmiKFkcqAwf+cWDAwCxAQ/IsCOzSBEFihjlirxHaxgMHGp3te3El1OWgcX0jZ0Zinukf1hti9McyWX6vOUp2u+lTs31viwPbWlYMdmjQoAjyDGfbk9EQDHnvJymxvzi+OvX6dSPDjl6rrxQYZy+lfi/LAFLGl30rV+WIfA1le4f9404D2MhY4FybZHGSVN6U6ThnQjF/w9rOnDwaCCGSQMBh2lTFUVLBQqo5MYXYhU/E8fsO6OoXZigBD/J3DW9dE4LF3XYD3yjliPj1M6jMnGYZ/OUL8nYPlDNl3rLts3n5ea/uA1LZU8kupjskHy5glaeFkhH/YIg2PNet0rPuwfGLZs0d6YrJBtmBdHr+UdUxe2CE6PC3s6Ojo6OhY+/kfWUsrD7TNg40AAAAASUVORK5CYII=>