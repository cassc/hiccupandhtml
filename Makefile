deploy:
	lein with-profile prod do clean, cljsbuild once
