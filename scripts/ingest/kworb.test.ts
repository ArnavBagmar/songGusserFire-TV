import assert from "node:assert/strict";
import { test } from "node:test";
import { kworbKey, parseKworbSongs } from "./kworb.ts";

const SAMPLE = `
<table><thead><tr><th class="text">Artist and Title</th><th>Streams</th><th>Daily</th></tr></thead><tbody>
<tr><td class="text"><div>The Weeknd - Blinding Lights</div></td><td>5,572,471,302</td><td>1,476,283</td></tr>
<tr><td class="text"><div>Ed Sheeran - Shape of You</div></td><td>5,076,426,436</td><td>1,725,339</td></tr>
<tr><td class="text"><div>Beyonc&#233; &amp; JAY-Z - Crazy In Love</div></td><td>1,000,000</td><td>2,000</td></tr>
<tr><td class="text"><div><a href="x.html">Linked Artist</a> - Some Song - Remix</div></td><td>500,000</td><td>100</td></tr>
</tbody></table>`;

test("parses artist, title, streams, daily and position from kworb rows", () => {
  const songs = parseKworbSongs(SAMPLE);
  assert.equal(songs.length, 4);
  assert.deepEqual(songs[0], {
    artist: "The Weeknd",
    title: "Blinding Lights",
    totalStreams: 5572471302,
    dailyStreams: 1476283,
    position: 1,
  });
  assert.equal(songs[1].dailyStreams, 1725339);
});

test("decodes HTML entities in names", () => {
  const songs = parseKworbSongs(SAMPLE);
  assert.equal(songs[2].artist, "Beyoncé & JAY-Z");
  assert.equal(songs[2].title, "Crazy In Love");
});

test("strips inner tags and splits on the first dash only", () => {
  const songs = parseKworbSongs(SAMPLE);
  assert.equal(songs[3].artist, "Linked Artist");
  assert.equal(songs[3].title, "Some Song - Remix");
});

test("empty or garbage input yields no songs", () => {
  assert.deepEqual(parseKworbSongs(""), []);
  assert.deepEqual(parseKworbSongs("<html><body>nope</body></html>"), []);
});

test("kworbKey normalizes case, punctuation and whitespace", () => {
  assert.equal(kworbKey("The Weeknd", "Blinding Lights"), kworbKey("the weeknd", "BLINDING  LIGHTS!"));
  assert.notEqual(kworbKey("A", "Song"), kworbKey("B", "Song"));
});
