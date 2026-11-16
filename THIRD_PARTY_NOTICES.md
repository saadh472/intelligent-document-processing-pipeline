# Attribution and third-party notices

The repository-authored source code, documentation, and fictional demo fixtures are available under the [MIT license](LICENSE). Third-party libraries retain their original licenses and copyright notices.

## Main dependencies

| Component | Use | Upstream terms |
| --- | --- | --- |
| Spring Boot, Spring Framework, Spring Security, Spring Kafka | HTTP API, application configuration, security, persistence integration, messaging | [Apache-2.0; Spring Boot license](https://github.com/spring-projects/spring-boot/blob/main/LICENSE.txt), with the licenses and notices in each Spring artifact |
| Apache Tika | Document parsing and text extraction | [Apache-2.0 and bundled dependency notices](https://github.com/apache/tika/blob/main/LICENSE.txt); [Tika NOTICE](https://github.com/apache/tika/blob/main/NOTICE.txt) |
| H2 Database Engine | Embedded database | [MPL-2.0 or EPL-1.0](https://www.h2database.com/html/license.html) |
| PostgreSQL JDBC driver | Optional PostgreSQL connection | [BSD-2-Clause; PostgreSQL Global Development Group](https://github.com/pgjdbc/pgjdbc/blob/master/LICENSE) |
| React and React DOM | Browser interface | [MIT; Meta Platforms and affiliates](https://github.com/facebook/react/blob/main/LICENSE) |
| MUI Material and icons | Interface components and icons | [MIT; Material UI contributors](https://github.com/mui/material-ui/blob/master/LICENSE) |
| Emotion | Component styling | [MIT; Emotion authors](https://github.com/emotion-js/emotion/blob/main/LICENSE) |
| D3 | Pipeline visualization | [ISC; Mike Bostock](https://github.com/d3/d3/blob/main/LICENSE) |
| Axios, React Dropzone, React Router, SockJS client | HTTP requests, file selection, routing, event transport | MIT; see the installed packages' license files and `frontend/package-lock.json` |
| stompjs | STOMP event client | [Apache-2.0](https://github.com/jmesnil/stomp-websocket/blob/master/LICENSE.txt) |

Backend dependencies and managed versions are declared by `backend/pom.xml`; resolved frontend versions and license identifiers are recorded in `frontend/package-lock.json`. Build and test tools, and dependencies pulled in by Tika parsers, also carry their own terms. This summary does not assign a single license to all transitive dependencies.

## Redistribution

Keep the upstream `LICENSE` and `NOTICE` files included with Maven artifacts, Python or npm packages, and generated bundles. In particular, retain any frontend `*.LICENSE.txt` files emitted by the production build alongside their bundles. This document is a navigation aid and does not replace those complete notices.

H2 remains an unmodified third-party database engine; its source is available from the [H2 project](https://github.com/h2database/h2database). Its license choice and disclaimer remain those distributed with the installed H2 artifact. No project license grant extends to private documents uploaded by application users.

