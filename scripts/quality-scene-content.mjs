/** Authored content for the disposable local review database. No production seed migration. */
export const users = [
  { firstName: 'Ari', lastName: 'Rivera', displayName: 'Ari Rivera', email: 'ari@pulse.local' },
  { firstName: 'Mina', lastName: 'Park', displayName: 'Mina Park', email: 'mina@pulse.local' },
  { firstName: 'Jonah', lastName: 'Reed', displayName: 'Jonah Reed', email: 'jonah@pulse.local' },
  { firstName: 'Talia', lastName: 'North', displayName: 'Talia North', email: 'talia@pulse.local' },
  { firstName: 'Elio', lastName: 'Mercer', displayName: 'Elio Mercer', email: 'elio@pulse.local' },
  { firstName: 'Sasha', lastName: 'Chen', displayName: 'Sasha Chen', email: 'sasha@pulse.local' },
  { firstName: 'Nico', lastName: 'Santos', displayName: 'Nico Santos', email: 'nico@pulse.local' },
  { firstName: 'Imani', lastName: 'Brooks', displayName: 'Imani Brooks', email: 'imani@pulse.local' },
  { firstName: 'Kai', lastName: 'Tanaka', displayName: 'Kai Tanaka', email: 'kai@pulse.local' },
  { firstName: 'Lena', lastName: 'Sol', displayName: 'Lena Sol', email: 'lena@pulse.local' },
];

// Ten families, ten variations per family. The render catalog uses this same order.
// A tuple contains a title, a description, and two scene-specific comments.
// A comment tuple contains the parent text followed by a short reply conversation.
const families = [
  {
    tags: ['Orbit', 'Geometry', 'Ambient', 'Reactive'],
    entries: [
      ['A Place to Land', 'I kept coming back to these rings after trying much busier versions. They have enough movement to hold my attention without asking for all of it. Put something slow on and stay a while.', [
        ['Could you leave this version up if you make a faster one? The pace is the best part.', 'Absolutely. Any faster version will be a separate scene.'],
        'This is the first thing I open when I finish work. Those few minutes of doing nothing help.',
      ]],
      ['Satellite Hour', 'Made this after a late train ride, when every light outside looked like it was moving around us. The rings ended up much tidier than my original idea, and I decided to let them be.', [
        'The train-window comparison makes sense. It has that same feeling of moving while sitting still.',
        'I like that there is a clear centre to come back to. Some scenes make my eyes work too hard.',
      ]],
      ['Room for One More', 'There is always space between these rings, even when they seem about to meet. I was trying to make something welcoming. That is a vague brief, but this feels close.', [
        ['Was the space between the rings the starting point?', 'Yes, actually. The first draft was almost entirely empty space.'],
        'A nice change from everything being packed right up to the edges.',
      ]],
      ['Small Hours', 'A small, quiet orbit for the part of the night when the neighbours have finally stopped moving furniture. I kept the whole thing fairly restrained. It does not need to turn into a light show.', [
        'The neighbours moving furniture line is painfully accurate.',
        'I have been looking for something this simple for my second screen.',
      ]],
      ['Return Ticket', 'I wanted the motion to feel like leaving and coming back to the same place. Rings turned out to be a better answer than the complicated shapes I started with. This is the version I kept.', [
        'There is something reassuring about knowing where it will come back to.',
        ['Would love to see the rejected version too.', 'It mostly looked like a drawer full of cables. This one earned its place.'],
      ]],
      ['Three Stops from Home', 'This started as a little waiting-room experiment and became the scene I leave running most often. Nothing dramatic, just an orbit with a bit of breathing room around it.', [
        'A waiting room with this playing would be a substantial improvement.',
        'The empty space does a lot of work here. Glad you did not fill it.',
      ]],
      ['Almost in Alignment', 'The interesting part of an orbit is when things nearly line up. I tried to keep that moment visible without turning the whole scene into a puzzle. Watch it casually; there is nothing to solve.', [
        ['I keep trying to catch the alignment anyway.', 'Same. I wrote the description as a reminder to myself.'],
        'The slight imbalance makes it feel less like a loading indicator, which I appreciate.',
      ]],
      ['Kitchen Radio', 'Something simple to have on while making dinner. I liked the idea of a tiny set of orbits keeping time on the counter, somewhere between a clock and a houseplant.', [
        'A clock and a houseplant is a surprisingly good description of what I want from this site.',
        'I put this beside a recipe and promptly forgot the onions. No regrets about the scene though.',
      ]],
      ['A Little Further Out', 'I widened the spacing until the rings could feel separate without losing each other. It reminds me of looking at a town from the hills above it. The mood mattered more than the reference.', [
        'That sense of distance comes through even though the shapes are simple.',
        'I prefer this spacing to the tighter orbit scenes. Easier to settle into.',
      ]],
      ['Night Bus Window', 'An orbit for the last bus home. I wanted a steady point to look at, with everything else quietly finding its way around it. This one took fewer changes than I expected.', [
        ['Did you build this with a particular track in mind?', 'No single track. Mostly the feeling of a very long ride home.'],
        'Saved for those evenings when a whole film feels like too much.',
      ]],
    ],
  },
  {
    tags: ['Frames', 'Geometry', 'Minimal', 'Reactive'],
    entries: [
      ['Rooms Inside Rooms', 'I have a habit of drawing boxes in the margins of notebooks. This is what happened when I gave one of those drawings some depth and let it move. The gaps are my favourite part.', [
        'This looks like the sketch I always tried to make in maths class, except it actually works.',
        ['Have you tried making the frames thicker?', 'I did, but the spaces started to disappear. I preferred this balance.'],
      ]],
      ['The Empty Apartment', 'All the furniture is gone, but the rooms still seem to hold a shape. I wanted to keep that feeling here: a few nested frames and plenty of air between them.', [
        'This somehow feels nostalgic for an apartment I have never lived in.',
        'Glad you kept it empty. Adding objects would have changed the whole mood.',
      ]],
      ['Stack of Possibilities', 'A handful of frames, each suggesting a slightly different space. I kept trying to choose one and eventually realised that keeping all of them was the point.', [
        'The title fits without explaining too much.',
        ['I would happily stare at this in a gallery.', 'I would happily accept a gallery with comfortable chairs.'],
      ]],
      ['Open Plan', 'This is my attempt at architecture without having to worry about doors, stairs, or where the bathroom goes. Just a few clear edges and a changing view through them.', [
        'Finally, a floor plan where I cannot trip over a chair.',
        'The lines stay readable even in the small preview. That is hard to get right.',
      ]],
      ['Keep the Door Open', 'There was a solid cube here originally. Removing its faces made it feel a lot less closed off, so I followed that idea. Now there is a way through every side.', [
        ['Would a solid centre ruin it?', 'For this one I think so, but it might make a good companion scene.'],
        'The open edges are exactly what caught my eye in the thumbnail.',
      ]],
      ['Little Framework', 'I made this on a day when I could not finish anything complicated. A few boxes were manageable. Sometimes a small idea gets further when I stop trying to make it important.', [
        'This is a good reminder that finishing something small still counts.',
        'The modest scale is part of the charm. Please do not turn it into a giant fortress.',
      ]],
      ['Borrowed Space', 'Nested frames that never quite feel like separate rooms. I was thinking about shared studios and those temporary boundaries people build around a desk. This is much less cluttered than my actual desk.', [
        'My desk would need at least twelve more layers of chaos.',
        ['Could this work as a backdrop for a live set?', 'That was in the back of my mind. Let me know if you try it.'],
      ]],
      ['Inside the Outline', 'The outside edges are easy to notice. I spent most of the time looking at the smaller spaces they make together. There is a surprising amount to see in an almost empty object.', [
        'Once you mentioned the inner spaces I could not stop looking at them.',
        'Feels a little like one of those impossible drawing exercises, in a good way.',
      ]],
      ['Window Seat', 'A view made entirely of windows, with no scenery outside to distract from them. I wanted something orderly but not rigid. The gentle movement helped with that.', [
        ['A version with one frame missing could be interesting.', 'I like that idea. It might give the whole thing a different balance.'],
        'Orderly but not rigid is exactly the mood I get.',
      ]],
      ['Under Construction', 'No grand concept this time. I liked these frames while I was putting them together and decided to stop before I covered them up. Consider it a finished unfinished building.', [
        'Stopping at the right moment is half of making anything.',
        'The title made me expect a work in progress, but it feels complete to me.',
      ]],
    ],
  },
  {
    tags: ['Spheres', 'Abstract', 'Soft', 'Reactive'],
    entries: [
      ['Pebbles in a Pocket', 'A small collection of round shapes, close enough to feel like a group. I was thinking of the stones that somehow come home from every walk. There is no sensible reason to keep them, but I do.', [
        'I also have a completely unnecessary collection of very important stones.',
        ['Did you want them to look like actual pebbles?', 'Only in the sense of being a small group worth keeping. The shapes can stay abstract.'],
      ]],
      ['Good Company', 'I wanted these little spheres to feel comfortable together. Not arranged into a perfect diagram, just sharing the same bit of space. It is a small idea, and I am happy to leave it small.', [
        'They do look like they know each other.',
        'This is oddly reassuring after a long day of looking at spreadsheets.',
      ]],
      ['Loose Change', 'This began with the things left in a coat pocket at the end of winter. I traded the actual objects for simple round forms and kept the feeling of a small, accidental collection.', [
        ['Mine would need a bus ticket and one unidentifiable sweet.', 'The bus ticket might deserve its own scene.'],
        'I like a scene with a mundane starting point. Makes the abstraction easier to connect with.',
      ]],
      ['Gathering Point', 'A few shapes meeting in the middle, with room for each one to be seen. I kept returning to the spacing rather than adding more detail. That turned out to be the useful part of the exercise.', [
        'The spacing is what makes this one for me. Nothing feels squeezed in.',
        'I would like this as a little object on my desk.',
      ]],
      ['Not Quite a Constellation', 'I thought about naming these after stars, but they feel much closer than that. More like a collection on a shelf. There is a bit of sky in it, just not an entire universe.', [
        'Thank you for resisting another galaxy title. The smaller scale is lovely.',
        ['They remind me of a model from a science classroom.', 'That is probably closer to what I had in mind than the stars were.'],
      ]],
      ['Bowl of Marbles', 'There is a jar of old marbles in my kitchen that never gets used for anything. This is a loose tribute to it. I wanted simple shapes that looked worth keeping around.', [
        'My grandparents had exactly that jar. I can hear the lid coming off.',
        'The round forms make a nice break from all the sharp geometric scenes.',
      ]],
      ['After the Meeting', 'A little cluster that looks more relaxed than anything on my calendar. I made this after closing a lot of tabs and deciding one thing on the screen was enough.', [
        ['How many tabs is a lot?', 'Enough that finding the one playing sound had become a regular activity.'],
        'This title alone earned a save from me.',
      ]],
      ['Six Degrees of Quiet', 'This one came from wanting a group of shapes that did not compete with each other. The individual pieces are simple; how they sit together is what I kept adjusting.', [
        'It has the feeling of a group conversation where nobody is interrupting.',
        'I like that there is not one huge object bossing the others around.',
      ]],
      ['Collected on Sunday', 'No plan, just a collection of small round things and an afternoon with nowhere to be. I kept the result close to that first sketch. Overthinking it would have missed the point.', [
        ['This makes me want a whole series named after ordinary afternoons.', 'I seem to be making that series by accident.'],
        'Exactly the sort of unhurried thing I was looking for.',
      ]],
      ['Soft Landing', 'I wanted to make something with no sharp edges. That sounds like a very low bar until you look at how many boxes I usually make. These little spheres were a welcome change.', [
        'As a fellow serial box-maker, I respect the restraint.',
        'This would be a good first scene to show someone who finds the busier ones overwhelming.',
      ]],
    ],
  },
  {
    tags: ['Loops', 'Geometry', 'Sculpture', 'Reactive'],
    entries: [
      ['Two Ways Around', 'Two loops sharing the same space without becoming the same thing. I kept changing their relationship by tiny amounts. The simplest version was the one that finally felt right.', [
        ['It is surprising how much personality two loops can have.', 'I spent far too long treating them like characters.'],
        'The silhouette is really clear. I knew which scene this was from the small card.',
      ]],
      ['A Useful Knot', 'I cannot tie a useful knot in real life, so I made an abstract one instead. This pair of loops is more about how the shapes meet than whether it would hold a boat in place.', [
        'As someone who sails, please keep this away from boats. As someone with eyes, I love it.',
        'A knot with no practical application is my kind of project.',
      ]],
      ['Crossed Paths', 'The loops started apart and gradually moved into each other while I worked. I liked the point where they became a pair without losing their separate outlines. That is where I stopped.', [
        'I keep looking at the space where they cross rather than the loops themselves.',
        ['Would it be possible to make them swap places?', 'Possibly, but I want to keep this version more settled.'],
      ]],
      ['Holding Pattern', 'Something about two interlocking shapes feels patient to me. This scene is for the gaps between plans: waiting for a call, waiting for dinner, waiting for an idea to arrive.', [
        'I watched this while waiting for a download. Best use of that time so far.',
        'Patient is the right word. It does not feel like it is trying to get somewhere.',
      ]],
      ['The Long Way Round', 'I kept looking for a more direct shape and ended up with two loops. Apparently I prefer the scenic route. There is no big reveal; the whole idea is already here.', [
        ['Appreciate the warning about the reveal. I can stop waiting for an explosion now.', 'No explosions scheduled.'],
        'Some scenes are better when they are content to just exist.',
      ]],
      ['Common Ground', 'A little study of two shapes that need the same space. Neither one gets the whole screen to itself. I liked the balance enough to keep the rest very simple.', [
        'The pair feels evenly matched. One does not look like an accessory to the other.',
        'This has a surprisingly friendly mood for such simple geometry.',
      ]],
      ['Second Opinion', 'I made one loop, thought it looked lonely, and added another. Most of the work after that was undoing things I did not need. Here is the version with the unnecessary parts removed.', [
        ['What was the most unnecessary part?', 'A third loop. It was very keen to be involved.'],
        'The second loop was a good call. It gives the first one something to do.',
      ]],
      ['Back and Forth', 'This feels like a conversation that keeps finding its way back to the same subject. Two rounded forms, no sharp interruption. I wanted the scene to feel easy to return to.', [
        'The description reminds me of talking with an old friend after months apart.',
        'I have returned to this one enough times that the title is becoming literal.',
      ]],
      ['A Small Agreement', 'Two loops agreeing, for now, to occupy one frame. I was aiming for something balanced without making it perfectly symmetrical. A tiny bit of awkwardness seemed worth keeping.', [
        ['The awkwardness is what makes it feel intentional.', 'That was the hope. Too neat started to feel like a logo.'],
        'I enjoy the small sculpture feeling. It looks like it could fit in my hands.',
      ]],
      ['No Loose Ends', 'I like making closed shapes because there is nowhere obvious to start or finish. These two loops are a small attempt to make that feeling visible. Follow either one for as long as you like.', [
        'I followed one around and then immediately forgot which one I started with.',
        'The lack of a clear beginning is the nicest part. Easy to dip in and out.',
      ]],
    ],
  },
  {
    tags: ['Rings', 'Sculpture', 'Rhythm', 'Reactive'],
    entries: [
      ['Better in Layers', 'I started with one ring and kept adding another until the stack felt complete. Then I removed a few. This is the point between too little and too much that I could live with.', [
        ['Removing a few is always the hard part.', 'I saved a copy before doing it. That made the decision much easier.'],
        'It has a nice vertical rhythm without feeling like a diagram.',
      ]],
      ['The Quiet Tower', 'A tower without windows or anything important at the top. I wanted to see how little it takes for a stack of rings to feel like a place. Turns out it does not take much.', [
        'I can somehow imagine a very small person being impressed by this building.',
        'The spaces between the levels matter as much as the rings. Nice balance.',
      ]],
      ['Waiting for the Lift', 'This reminds me of looking through the open floors of a building before the walls go in. There is a little order to it, but I did not want it to become a technical drawing.', [
        'It does have that unfinished building feeling, but much more peaceful.',
        ['A technical drawing version could be a fun contrast.', 'I might try one with everything a little more strict.'],
      ]],
      ['Stacked Cups', 'The original reference was much less elegant: a pile of cups beside the sink. I borrowed the repeated shape and left the washing up out of it. Some inspirations are better simplified.', [
        'My washing up has never looked this organised.',
        'I appreciate knowing that this started in a kitchen rather than outer space.',
      ]],
      ['One Thing at a Time', 'This is a stack built one small decision at a time. I wanted each layer to be easy to pick out, without losing the sense of a single object. It helped me slow down while making it.', [
        ['That is exactly how I need to approach the rest of my afternoon.', 'Start with the smallest thing. It worked here.'],
        'The whole object reads clearly, but there are enough gaps to keep looking through.',
      ]],
      ['Radio Furniture', 'A small imaginary piece of furniture for a room that only exists while music is on. It does not have a useful function. I mostly wanted it to stand there with a bit of confidence.', [
        'I would put a very impractical lamp on this.',
        'It does have furniture energy. Not sure why, but it does.',
      ]],
      ['Some Assembly Required', 'A few repeated parts, carefully spaced, and nothing hidden behind a cover. I enjoy objects where you can see how they are put together. This is a little nod to those.', [
        ['Instructions unclear, built a space accordion.', 'Honestly that is an acceptable outcome.'],
        'I like that the construction is the decoration. No extra bits needed.',
      ]],
      ['Floors Without Walls', 'I was thinking about the first few weeks on a building site, when you can still see through everything. These open rings keep that temporary feeling without needing an actual building.', [
        'A structure that never has to reach the boring finished stage.',
        'The negative space is doing the heavy lifting here. Literally, perhaps.',
      ]],
      ['A Steady Stack', 'This version is deliberately straightforward. A stack of rings, some space between them, and a clear shape against the background. I wanted the small changes to be enough.', [
        ['Would you make a much taller one?', 'Maybe as a separate piece. I like the proportions of this stack.'],
        'Straightforward is underrated. I knew what I liked about it immediately.',
      ]],
      ['Shelves for Nothing', 'An object with all the suggestion of storage and nowhere to put anything. I liked that contradiction. It is a little too open to be practical, which is probably why it works here.', [
        'Finally, shelves my cat cannot knock things off.',
        'This would be the least useful and most interesting thing in my living room.',
      ]],
    ],
  },
  {
    tags: ['Radial', 'Spheres', 'Rhythm', 'Reactive'],
    entries: [
      ['Around the Table', 'A circle of small forms, with an empty place in the middle. I was thinking about the shape people make when a conversation is going well and nobody is trying to leave.', [
        ['The empty centre makes this feel surprisingly sociable.', 'I tried putting something there, but it was better without it.'],
        'Now I am imagining each little shape having a completely different opinion.',
      ]],
      ['Pocket Compass', 'I wanted something round and legible, like an instrument with its labels removed. It will not tell you where north is, but it gives my eyes somewhere comfortable to settle.', [
        'A compass that helps with absolutely none of my navigation problems. Saved.',
        'The radial layout feels familiar without looking like a specific object.',
      ]],
      ['Counting Backwards', 'A small ring of beads for the times when my brain needs a very simple task. I did not give them numbers. You can count, lose your place, or just watch the whole circle.', [
        'I have lost count four times, which might mean it is working.',
        ['Was this inspired by worry beads?', 'A little. Mostly by the way a simple repeated shape can occupy your hands or eyes.'],
      ]],
      ['Circle of Friends', 'The name arrived before the scene did. I wanted a group of simple forms arranged so that none of them felt left out. There is not much more to explain, which is nice for a change.', [
        'An uncomplicated idea, kindly done.',
        'I like that no bead gets to be the main character.',
      ]],
      ['Spare Buttons', 'My grandmother kept spare buttons in a tin, even when nobody knew which clothes they belonged to. This is a much more orderly arrangement than hers, but the fondness is the same.', [
        ['We had the same tin. There was always one enormous button.', 'Ours had at least three that could never have belonged to normal clothing.'],
        'I love that a simple circle can carry such an ordinary memory.',
      ]],
      ['A Short Walk Around', 'There is no particular destination here. I liked the idea of a complete little route that fits on a screen. Something you can follow once or keep coming back to.', [
        'A walk I can manage while my tea is still too hot.',
        'This is more relaxing than it has any right to be.',
      ]],
      ['Each in Its Place', 'A radial arrangement for a day when everything else was scattered. I tried to keep it orderly without making it stern. The rounded shapes helped soften the whole thing.', [
        ['This is what I wish organising my desk felt like.', 'The digital desk is much more cooperative.'],
        'The rounded forms stop it feeling like a chart. Good choice.',
      ]],
      ['The Roundabout', 'I grew up near a tiny roundabout that everyone treated as optional. This is a better behaved circle, made from simple beads. It has no connection to traffic rules, thankfully.', [
        'Where I grew up, a roundabout was mostly a place to argue about right of way.',
        'The backstory is oddly specific and makes me like this more.',
      ]],
      ['A Small Ceremony', 'Repeated shapes arranged with more care than their size would suggest. It reminds me of setting a table for no special reason. Sometimes arranging things nicely is enough of a reason.', [
        ['Setting the table properly for yourself counts as an occasion.', 'Agreed. That is more or less the whole thought behind this.'],
        'There is a pleasing amount of care in something so simple.',
      ]],
      ['Sunday Round', 'A circular arrangement with no urgency in it. I made this while avoiding a list of much more practical things to do. The list is still there, but at least this is finished.', [
        'My practical list would like a word with both of us.',
        'Sometimes finishing a small creative thing is the practical thing.',
      ]],
    ],
  },
  {
    tags: ['Blocks', 'Geometry', 'Sculpture', 'Reactive'],
    entries: [
      ['Things with Edges', 'After making a run of rounded scenes, I wanted something with corners again. These blocks feel a little more definite. I kept the arrangement simple so the edges could do the talking.', [
        ['The sharper shapes are a good change of pace.', 'I needed the contrast too. Everything was starting to feel like a pebble.'],
        'This feels more like an object than an effect, which I like.',
      ]],
      ['Found in a Drawer', 'A handful of solid shapes that could belong to a game nobody remembers. I wanted them to feel a little familiar without being identifiable. You can invent the rules if you like.', [
        'The rules are clearly lost, along with one very important piece.',
        'I am choosing to believe this is a game I would be good at.',
      ]],
      ['Solid Ground', 'Sometimes I want an object that looks like it has a bit of weight. This is one of those scenes. A few simple blocks, kept visible, with enough space around them to feel settled.', [
        'The sense of weight is what made me stop scrolling.',
        ['A darker version could be interesting too.', 'I may revisit it, but I want to keep the edges readable.'],
      ]],
      ['Corner Shop', 'The title is mostly a small joke about corners. I made a few blocks, found an arrangement I liked, and resisted the urge to give it a much grander explanation.', [
        'I respect a scene that admits the title is a pun.',
        'No grand explanation needed. The composition carries it.',
      ]],
      ['Offcuts', 'These feel like the pieces left over after making something else. I wanted to give that accidental collection a little attention. Nothing here needs to become a bigger object.', [
        ['I always like the offcuts more than the thing I was meant to build.', 'There is less pressure on them. Maybe that is why.'],
        'A nice argument for not throwing the small ideas away.',
      ]],
      ['Paperweight Study', 'An imaginary paperweight for a desk with no paper on it. I was looking for a compact shape that felt substantial without taking over the screen. This is where I ended up.', [
        'I would buy the physical version and immediately lose it under actual papers.',
        'The proportions are satisfying. It feels deliberately sized.',
      ]],
      ['A Few Good Angles', 'I kept rotating the arrangement while working because every angle seemed to suggest a different little object. Rather than pick one view, I left some room for that uncertainty.', [
        ['It keeps switching between a sculpture and something from a board game for me.', 'That is a pretty good summary of my reference folder.'],
        'The corners give it a nice sense of structure without making it too severe.',
      ]],
      ['Hard Candy', 'Simple solid forms with the slightly unreasonable appeal of sweets behind glass. I was not trying to make food, but the association stuck. Please do not ask me what flavour they are.', [
        'Too late. I have already assigned three flavours.',
        'This is making me want the old-fashioned sweets my dentist would hate.',
      ]],
      ['Shelf Life', 'A small arrangement of objects that could sit on a shelf for years without asking much of you. I wanted something that feels worth looking at again, even when it stays uncomplicated.', [
        ['I like the idea of a digital object that does not demand an update.', 'Exactly. Sometimes the first good version can just stay.'],
        'Feels like the sort of thing I would bring home from a museum shop.',
      ]],
      ['The Shape of Enough', 'I stopped adding pieces when the arrangement started to feel crowded. Then I took one more away. This is my reminder that an object can be finished before every gap is filled.', [
        'The last piece you remove is often the most useful decision.',
        'There is a comfortable amount of space around this. It makes the whole scene easier to read.',
      ]],
    ],
  },
  {
    tags: ['Hoops', 'Geometry', 'Minimal', 'Reactive'],
    entries: [
      ['Looking Through', 'A few hoops, one inside another, with the centre left open. I was more interested in the view through them than in the hoops themselves. It is a simple place for your eyes to rest.', [
        ['The empty centre is doing something to my sense of depth.', 'That is the bit I kept watching while I made it.'],
        'This was an immediate save. Very little happening, in the best way.',
      ]],
      ['Target Practice', 'A target with nothing to hit and no score to chase. I liked the familiar arrangement of rings but wanted to take all the urgency out of it. You can just look.', [
        'Finally, a target I cannot miss.',
        'Taking the score away is a surprisingly effective design decision.',
      ]],
      ['Widening Circles', 'I have been drawing concentric circles since I was a kid. Giving them a little depth did not make the idea any less satisfying. This version keeps the arrangement easy to see.', [
        'Some doodles remain good for your entire life.',
        ['Did you try an off-centre version?', 'I did. I might keep that for another scene because it changes the mood quite a lot.'],
      ]],
      ['Through the Middle', 'There is a natural temptation to put something in the middle of a composition. I left this one empty on purpose. The surrounding hoops seemed more interesting when they had nothing to point at.', [
        'The empty space feels like a real choice, not a missing object.',
        'I like how much depth you get from a few simple rings.',
      ]],
      ['Circles I Drew in Class', 'A cleaned-up version of a very old notebook habit. I used to draw these until the lines ran into each other. Here they get to stay separate, which is much kinder to the paper.', [
        ['Mine always turned into a spiral by accident.', 'Mine too. Keeping circles closed was apparently the hard part.'],
        'This takes me straight back to the last ten minutes of a very long lesson.',
      ]],
      ['A Little Perspective', 'I wanted a scene that could suggest distance using almost nothing. A few hoops were enough. It is not meant to be a tunnel to anywhere, though I understand the temptation to look for the end.', [
        'I was absolutely looking for the end before reading this.',
        'The depth feels convincing without a busy background behind it.',
      ]],
      ['Open Centre', 'I kept the middle clear and built the scene around that decision. It feels less like an object and more like a space somebody has made for you. I like that shift.', [
        ['This has a very different mood from the scenes with a bright core.', 'Leaving the core out changed more than I expected.'],
        'One of my favourites for looking away from a crowded desktop.',
      ]],
      ['Long Exhale', 'This is the scene I made when everything else I tried felt too busy. Hoops, clear spacing, and an open centre. I did not need another idea on top of that.', [
        'The title gave me a useful reminder to unclench my jaw.',
        'It feels complete without having to fill every bit of the frame.',
      ]],
      ['The View from Here', 'Same simple shape, repeated at a few sizes, until it starts to feel like a view. I enjoy how a small change in spacing can turn an arrangement into a place.', [
        ['Do you think of these as objects or spaces?', 'This one changed sides while I was making it. I started with an object and ended with a view.'],
        'The title leaves enough room to bring my own associations to it.',
      ]],
      ['Nothing in the Way', 'An open circle is a nice thing to end the day with. I kept coming back to this arrangement whenever the other versions got too elaborate. Sometimes that is the answer.', [
        'I appreciate that the simple version got to be the finished one.',
        'The open middle feels like permission to stop concentrating for a minute.',
      ]],
    ],
  },
  {
    tags: ['Pillars', 'Geometry', 'Architecture', 'Reactive'],
    entries: [
      ['Columns After Closing', 'A small collection of pillars with nobody around them. I was thinking of public spaces after everyone has gone home, when the building gets to be quiet for a while.', [
        ['Empty buildings have a completely different personality.', 'Yes. I was trying to keep a little of that without making it eerie.'],
        'It feels like the last five minutes in a museum before they ask you to leave.',
      ]],
      ['Standing Room', 'A few upright forms sharing a small patch of space. I wanted them to feel steady without looking like they had been placed with a ruler. This is the arrangement I kept returning to.', [
        'The slight looseness in the arrangement gives it some character.',
        'I like that it feels architectural without depicting a particular building.',
      ]],
      ['Between Stations', 'I kept thinking about the pillars along a station platform, especially when it is nearly empty. This is only a loose connection, but it gave the simple shapes somewhere to begin.', [
        'I can almost hear the very distant announcement that nobody understands.',
        ['The station reference makes it feel familiar.', 'That is what I was hoping for, without copying a real place.'],
      ]],
      ['The Old Arcade', 'Not the video-game kind: the row of columns outside a shop where you can wait for the rain to stop. I borrowed the repeated upright shapes and kept the scene fairly spare.', [
        'I initially pictured arcade cabinets, so the clarification was useful.',
        'There is something nice about a scene inspired by a place to wait out the rain.',
      ]],
      ['Pillars of a Small Plan', 'Nothing monumental here. Just a few vertical forms that look like they could support an idea for an afternoon. I liked their scale and stopped before they became a skyline.', [
        ['An afternoon is about the right size for my plans too.', 'Same. Anything longer needs snacks and a backup plan.'],
        'Keeping the scale small makes it feel more personal than a city scene.',
      ]],
      ['Upright Company', 'I wanted a group of shapes that could stand together without marching in a line. These pillars are simple enough that their spacing becomes most of the scene.', [
        'They look like a group waiting politely for someone to take a photo.',
        'The spaces between them are easy to read, even in the thumbnail.',
      ]],
      ['A Shelter without a Roof', 'A few supports for an imaginary shelter. The roof never arrived, and I decided the scene was better that way. You can see the whole arrangement without anything being hidden.', [
        ['Not very useful in the rain, then.', 'Terrible shelter, reasonable scene.'],
        'I like the idea of leaving the implied building unfinished.',
      ]],
      ['Street after Rain', 'This started with a memory of walking home after the rain had stopped. I did not try to recreate the street. I just kept the upright shapes and the feeling of having the pavement to myself.', [
        'Glad you kept it abstract. A literal street would be a different sort of scene.',
        'That feeling of suddenly having a familiar place to yourself is lovely.',
      ]],
      ['Still Standing', 'A small arrangement of pillars that feels steady rather than grand. I was trying to make something dependable-looking. It is a strange ambition for a visual scene, but it helped me make decisions.', [
        ['Dependable-looking is a perfectly good brief.', 'Thank you. I was worried it sounded like a description of a kitchen appliance.'],
        'It does have a reassuring solidity to it.',
      ]],
      ['The Space Between Posts', 'I spent more time moving these apart than I did making their shapes. The useful part was finding room between them. Once that felt right, the scene did not need much else.', [
        'It is funny how often spacing turns out to be the entire project.',
        'The simple forms make those decisions visible. Nothing is hiding them.',
      ]],
    ],
  },
  {
    tags: ['Core', 'Frames', 'Abstract', 'Reactive'],
    entries: [
      ['Something Worth Keeping', 'A round core held inside an open frame. I wanted it to feel protected without being shut away. The space between the two shapes took longer to settle than either shape itself.', [
        ['Protected without being shut away is a lovely idea.', 'The open frame was the important part. A solid box changed the feeling completely.'],
        'This feels like a little object you would find and decide to bring home.',
      ]],
      ['The Heart of the Matter', 'I kept the centre simple and gave it a framework to sit inside. There was a much more elaborate version, but I could not tell where to look. This one makes that decision easier.', [
        'The clear focal point is what made me stop on this card.',
        'I like that the surrounding frame does not compete with the centre.',
      ]],
      ['Safe Passage', 'A small sphere with an open structure around it. I was thinking about carrying something delicate without hiding it completely. The result is more like a sculpture than a container.', [
        'It has the feeling of a careful little package.',
        ['Would you ever open the frame up further?', 'Maybe, but I like how it still reads as one object at this point.'],
      ]],
      ['A Place for the Centre', 'The frame came first. Adding a simple round centre gave the empty space a purpose, but I wanted to keep plenty of air around it. This is the balance that worked for me.', [
        'The gap between the sphere and the frame is the best part.',
        'It looks like the centre has room to breathe. Nice proportions.',
      ]],
      ['Precious Cargo', 'I liked the idea of giving an ordinary sphere an unnecessarily careful frame. It makes the simple object feel a little more important, like putting a very ordinary drawing in a good frame.', [
        ['An ordinary drawing in a good frame can change a whole room.', 'Exactly. Context does a surprising amount of work.'],
        'This is making me want to rescue a few things from my sketchbook.',
      ]],
      ['Held Lightly', 'An open frame around a soft-looking centre. I wanted the relationship to feel gentle, so I left a clear gap between the pieces. Nothing needs to be squeezed to stay together here.', [
        'The title suits the space between the shapes.',
        'A nice counterpoint to the denser frame scenes. This one feels less enclosed.',
      ]],
      ['A Small Observatory', 'This reminds me of a model built to study one very small imaginary planet. The reference is loose, but I liked having a central object and a structure that seemed interested in it.', [
        ['What would the observatory be measuring?', 'Probably whether the little planet is having a reasonable day.'],
        'The small scale makes it feel more curious than epic. I prefer that.',
      ]],
      ['Core Memory', 'A simple centre inside a structure that keeps finding new ways to frame it. I was thinking about how ordinary objects become attached to specific memories. This is deliberately open to your own.', [
        'It reminds me of a science museum display from when I was little.',
        'I appreciate a description that leaves some space for the viewer.',
      ]],
      ['Handle with Care', 'I nearly made the outer structure solid, then realised I would lose the thing I wanted to show. Keeping the edges open was the better choice. The centre can be seen from every side.', [
        ['The open structure makes the object feel less locked away.', 'That was the turning point for this version.'],
        'It has a satisfying balance of rounded and straight shapes.',
      ]],
      ['Home for a Small Idea', 'One simple object, a little framework, and enough room for both. This is the scene I ended on after trying a lot of bigger ideas. I think the smaller one deserved to stay.', [
        'A good place to finish the collection. It feels considered without being overworked.',
        'The small idea was worth keeping. This is one I will come back to.',
      ]],
    ],
  },
];

// Short reactions can recur across a community; the first two comments on each
// scene are individually authored above. These provide a varied conversation
// around them without embedding geometry claims that a scene cannot fulfil.
const conversation = [
  'I left this open while I made coffee and came back to it still feeling good.',
  'The restraint is what makes this work for me.',
  'I would probably slow it down a little, but I like the overall idea.',
  'This reads much better in full screen than I expected from the thumbnail.',
  'A welcome break after staring at a page full of text.',
  'I keep saving scenes and then returning to the same few. This is joining that group.',
  'The composition feels considered. Nothing is fighting for attention.',
  'I prefer a bit less glow, personally. The underlying shape is good though.',
  'This works well on a small screen too. That is not always the case.',
  'I had no particular reason to watch this for five minutes, but here we are.',
  'There is enough space around it that I can actually enjoy the shape.',
  'It is nice to find something that does not try to surprise me every few seconds.',
  'I like this more each time I come back to it.',
  'Not my usual style, but I stopped scrolling for this one.',
  'The thumbnail caught my eye and the full scene kept me here.',
  'This has earned a place in my collection of things to look at when my brain is full.',
  'I would love a slightly quieter version for late at night.',
  'Simple idea, well judged. I do not think it needs anything else.',
  'I disagree with the calls for more detail. The empty space is useful.',
  'Looks especially good with the rest of the room dimmed.',
  'I came here for a quick look and forgot what I was meant to be doing.',
  'There is a clear focal point, which makes this easy to settle into.',
  'My first reaction was that it was too simple. I have changed my mind.',
  'I would put this on a small display if I had one going spare.',
  'The proportions feel right. I cannot explain it more precisely than that.',
  'This is one of the few I wanted to save immediately.',
  'A little more contrast might help on my laptop, but it is still readable.',
  'I like that the description sounds like someone actually made a decision.',
  'The quieter scenes are the ones I tend to keep coming back to.',
  'I can imagine this sitting behind a set without stealing the whole show.',
  'This seems like a good starting point for someone new to making scenes.',
  'I tried looking away and coming back. The shape still reads instantly.',
  'The scale is good. Big enough to see, small enough to have some room.',
  'I might have pushed the effect further, but the restraint probably helps it last.',
  'Please keep this version if you decide to experiment with it later.',
  'An oddly pleasant thing to have beside a very boring task.',
  'It reminds me of an object I cannot quite place. That is a compliment.',
  'The edges are clear without looking harsh.',
  'I am glad there is not a lot happening in the background.',
  'This feels finished. Adding more would probably make it less interesting.',
  'I like it, though I would personally give the object a little more room.',
  'I keep finding a different small thing to look at.',
  'Saved this for later, then immediately watched it again.',
  'The mood of this is much calmer than my afternoon deserves.',
  'A good reminder that a few well-placed shapes can be enough.',
  'I would be curious to see an earlier draft beside this.',
  'This would make a nice little installation in a quiet corner.',
  'The title was what made me click. The scene is what made me stay.',
  'I am usually drawn to busy scenes, but this one makes a good case for less.',
  'It feels like something made to be watched for longer than a few seconds.',
  'I appreciate that I can tell what I am looking at straight away.',
  'A softer treatment might be nice, but I would keep this one too.',
  'There is a pleasing sense of balance here.',
  'I opened this while waiting for a friend. They are late, and I am less bothered now.',
  'The small preview did not quite prepare me for how much I would like the full view.',
  'This belongs in my after-work rotation.',
  'I would happily watch a short collection built around this idea.',
  'I was expecting something much busier from the name. Glad I was wrong.',
  'The shape looks deliberate from more than one angle.',
  'I like how little explanation this needs once you see it.',
  'There is something quite tactile about it, even on a screen.',
  'I would probably choose a different palette, but the arrangement works.',
  'A very good thing to look at while not checking my email.',
  'This is making me want to try building a scene myself.',
  'I like the slight sense of weight in the middle of all that space.',
  'The empty background is the right choice for this.',
  'I spent a while trying to think of what this reminds me of and gave up happily.',
  'This feels more like a small sculpture than a screen effect.',
  'I keep recommending the calmer scenes to friends. Adding this to the list.',
  'It looks good without needing every control turned up.',
  'There is a nice balance between something familiar and something abstract.',
  'I would give this more room on a big screen rather than enlarge the object.',
  'It holds together well even when I am only half paying attention.',
  'One of those ideas that looks obvious only after somebody makes it.',
  'The little imperfections in the arrangement make it more interesting to me.',
  'I nearly passed this by. Glad I gave it a proper look.',
  'This feels like it could belong in a collection of physical objects.',
  'I have no useful criticism. I just wanted to say I enjoyed it.',
  'I would watch this in a room where the chairs were comfortable.',
  'There is a good amount of breathing room around the main form.',
  'I thought I wanted something dramatic today. Apparently I wanted this.',
  'It is easy to look at without becoming forgettable.',
  'The simplicity makes small changes feel more noticeable.',
  'I could imagine a whole series of these with slightly different arrangements.',
  'I am on an older laptop and the composition is still easy to read.',
  'This is one of the few descriptions I read all the way through.',
  'I like being able to recognise it again in a row of thumbnails.',
  'A little slower would be my preference, but this is a good starting point.',
  'It has a clear identity without trying too hard.',
  'I watched this while a kettle boiled. That was a good few minutes.',
  'The relationship between the solid shape and the empty space is really nice.',
  'I would keep the composition exactly as it is.',
  'I do not usually comment, but this one made me stop for a bit.',
  'This feels like it belongs on a screen you can see from across the room.',
  'I like that it does not rely on a complicated background to feel complete.',
  'There is a small amount of tension in the arrangement that keeps it interesting.',
  'I can see myself returning to this when I want something familiar.',
  'The object feels like it has enough room to exist on its own terms.',
  'I am glad this stayed simple. The idea comes across clearly.',
  'This would work nicely as part of a quieter evening set.',
  'I would not change much. Perhaps just try a slightly different framing.',
  'I keep looking at the gaps instead of the shapes themselves.',
  'A satisfying little object to spend some time with.',
  'This is the sort of scene that makes a second monitor feel justified.',
  'I would like to see how you arrived at the final proportions.',
  'The first impression is clear, but there is enough here for another look.',
  'I did not expect to have a favourite today, but this might be it.',
  'The whole thing feels intentional without feeling overworked.',
  'I like that the centre stays easy to find.',
  'This has been keeping me company while I sort a pile of old photos.',
  'A nice antidote to an extremely crowded desktop.',
  'I am more interested in the shape than the effects here, which is good.',
  'I would probably save a version with a little less brightness as well.',
  'This is pleasantly difficult to describe to someone else.',
  'I opened several scenes and this is the tab I kept.',
  'It makes good use of a small idea. That is harder than it sounds.',
  'The composition has a bit of character without turning into a face.',
  'I like it more when I stop trying to work out what it is supposed to be.',
  'This would make a good companion to something with much busier shapes.',
  'I keep wishing I could turn it over in my hands.',
  'The description and the scene feel like they came from the same person.',
  'A little more distance from the camera might be interesting to try.',
  'This is a surprisingly good way to take a short break.',
  'It has enough structure to look at without giving me a task to complete.',
  'I appreciate a scene that is comfortable with a bit of empty space.',
  'There is something very satisfying about the overall outline.',
  'I would rather have ten thoughtful scenes like this than a hundred noisy ones.',
  'This one makes me want to clear everything else off the screen.',
  'I like the size of the idea. It does not need a bigger story.',
  'The simple shape made me notice details I would otherwise miss.',
  'I came back after a few days and liked it just as much.',
];

function comment(value) {
  return Array.isArray(value) ? { text: value[0], replies: value.slice(1) } : { text: value };
}

export const scenes = families.flatMap((family, familyIndex) => family.entries.map((entry, variation) => {
  const index = familyIndex * 10 + variation;
  // A permutation makes popularity varied within every creator and shape family.
  // The first scene is a deliberate popular candidate for the homepage feature.
  const popularity = Number(((99 - (index * 37) % 100) / 99).toFixed(3));
  const commentCount = 2 + Math.round(popularity * 10);
  const comments = entry[2].map(comment);
  for (let extra = 0; comments.length < commentCount; extra++) {
    const text = conversation[(index * 17 + extra * 13) % conversation.length];
    if (!comments.some(existing => existing.text === text)) comments.push({ text });
  }
  return {
    ownerIndex: index % users.length,
    familyIndex,
    name: entry[0],
    description: entry[1],
    tags: [...family.tags],
    popularity,
    comments,
  };
}));
