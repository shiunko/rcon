(defproject rcon "0.1.0"
  :description "use to connect minecraft cron"
  :url "https://www.zthc.net"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :repositories [["central" "https://repo1.maven.org/maven2/"]
                 ["clojars" "https://mirrors.tuna.tsinghua.edu.cn/clojars/"]
                 ["jcenter" "https://maven.aliyun.com/repository/public"]
                 ["mojang" "https://libraries.minecraft.net"]
                 ["jitpack" "https://jitpack.io"]
                 ["sponge" "https://repo.spongepowered.org/maven"]
                 ["velocity" "https://repo.velocitypowered.com/snapshots/"]
                 ["maven_central" "https://repo.maven.apache.org/maven2/"]
                 ["maven_central" "https://repo.maven.apache.org/maven2/"]]
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [aleph "0.4.7"]
                 [manifold "0.2.3"]
                 [smee/binary "0.5.5"]]
  :main ^:skip-aot rcon.core
  :target-path "target"
  :uberjar-name "rcon.jar"
  :profiles {:uberjar {:aot :all
                       :omit-source true
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
