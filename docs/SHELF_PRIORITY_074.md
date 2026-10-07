# 0.7.4: shelf and reading priorities

Shelf refresh runs before bulk chapter downloads. A refresh requested during downloads interrupts after the current chapter, imports shelf data, refreshes the visible shelf timestamp/list, and rebuilds the priority queue.

Download order: detected new shelf chapters; books visited at least three times; other unfinished books; manually completed books with fewer visits. Pins and full-download flags do not increase priority. Favorites retain pins for accessibility and sort by reading visits, then last read time.

Home uses twelve buttons in four rows: Continue / Recent / Favorites; Shelf / Updates / Downloaded; Refresh / Pause or Resume / Search; Account / Settings / Exit.

Downloaded includes only manually marked completed novels with a nonempty catalog, every chapter cached, and no detected pending shelf change. It does not infer translation completion. Pause persists across launches and suspends automatic downloads/read sync; Resume refreshes shelf first.

SQLite remains version 3; new settings use SharedPreferences. This does not guarantee installation of older versionCode APKs or restore server-side read markers. Device behavior still requires user confirmation on S4.
