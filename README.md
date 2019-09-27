# datasource-clj

A Clojure library designed to ... well, that part is up to you.

## Usage

Start Grafana

    docker run -d -p 3000:3000 --name=grafana \
        -e "GF_INSTALL_PLUGINS=grafana-simple-json-datasource" grafana/grafana

and configure a SimpleJson Datasource pointing to your host,
e.g. http://192.168.0.1:8080. Beware that localhost inside a Docker
container does not point to the Docker Host.

## License

Copyright © 2019 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
