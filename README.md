# Grafana SimpleJson Datasource Backend in Clojure

A Clojure Implementation of the Data Source Backend for
[SimpleJson](https://grafana.com/grafana/plugins/grafana-simple-json-datasource)
Grafana Plugin.  See also generic infos for
[plugins](https://grafana.com/docs/grafana/latest/plugins/developing/datasources/).

## Start Datasource & Grafana with Kubernetes

    kubectl create namespace datasource-clj
    kubectl config set-context --current --namespace=datasource-clj
    kubectl apply -k ./k3s

Then  point your  browser to  [URL](http://grafana.localhost).  FIXME:
provision     the     Dashbaord     from     &     Datasource     from
[resources](./resources)?  This  Grafana instance may make  use of the
datasource  running  in  the  same pod  at  http://localhost:8080  ---
localhost from the inside of container, not your laptop hosting k3s.

## Start Grafana with Docker

    docker run -d -p 3000:3000 --name=grafana \
        -e "GF_INSTALL_PLUGINS=grafana-simple-json-datasource" grafana/grafana

and  configure   a  SimpleJson  Datasource  pointing   to  your  host,
e.g. http://192.168.0.1:8080.   Beware that localhost inside  a Docker
container does not point to the Docker Host. You may need to configure
Proxy  in the  Container environment  if  you are  behind a  corporate
Firewall, e.g.:

    docker run ... -e "https_proxy=..." ...

## Export Datasources from Grafana

Grafana does not seem to offer a way to export Datasources, use this
[workaround](https://rmoff.net/2017/08/08/simple-export/import-of-data-sources-in-grafana/):

    mkdir -p dir && curl -s "http://$ip:3000/api/datasources" -u admin:password | jq -c -M '.[]' | split -l 1 - dir/

Here ths $ip could be the Pod IP.

## License

Copyright © 2019 Alexei Matveev <alexei.matveev@gmail.com>

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
