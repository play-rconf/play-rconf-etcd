# Play Remote Configuration - CoreOS etcd


[![Latest release](https://img.shields.io/badge/latest_release-19.11-orange.svg)](https://github.com/play-rconf/play-rconf-etcd/releases)
[![JitPack](https://img.shields.io/badge/JitPack-release~19.11-brightgreen.svg)](https://jitpack.io/#play-rconf/play-rconf-etcd)
[![Build](https://api.travis-ci.org/play-rconf/play-rconf-etcd.svg?branch=master)](https://travis-ci.org/play-rconf/play-rconf-etcd)
[![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](https://raw.githubusercontent.com/play-rconf/play-rconf-etcd/master/LICENSE)

Retrieves configuration from CoreOS etcd
*****

## About this project
In production, it is not always easy to manage the configuration files of a
Play Framework application, especially when it running on multiple servers.
The purpose of this project is to provide a simple way to use a remote
configuration with a Play Framework application.



## How to use

To enable this provider, just add the classpath `"io.playrconf.provider.EtcdProvider"`
and the following configuration:

```hocon
remote-configuration {

  ## CoreOS etcd
  # ~~~~~
  # Retrieves configuration from CoreOS etcd
  etcd {

    # API endpoint. HTTPS endpoint could be used,
    # but the SSL certificate must be valid
    endpoint = "http://127.0.0.1:2379/"
    endpoint = ${?REMOTECONF_ETCD_ENDPOINT}

    # Authentication username
    username = ""
    username = ${?REMOTECONF_ETCD_USERNAME}

    # Authentication password
    password = ""
    password = ${?REMOTECONF_ETCD_PASSWORD}

    # Prefix. Get only values with key beginning
    # with the configured prefix. With etcd, it
    # must be a directory.
    prefix = "/"
    prefix = ${?REMOTECONF_ETCD_PREFIX}
  }
}
```


## License
This project is released under terms of the [MIT license](https://raw.githubusercontent.com/play-rconf/play-rconf-etcd/master/LICENSE).
