# Grafana Json Data Source Backend in Clojure

A Clojure Implementation of the Data Source Backend for
[SimpleJson](https://grafana.com/grafana/plugins/grafana-simple-json-datasource)
Grafana Plugin and/or its more maintained
[SimPod](https://github.com/simPod/grafana-json-datasource) fork.  See
also generic infos for
[plugins](https://grafana.com/docs/grafana/latest/plugins/developing/datasources/).

## Data Source & Grafana with Kubernetes

Use e.g. [k3s](https://github.com/rancher/k3s) as no bullsh*t
Kubernetes.

    kubectl create namespace datasource-clj
    kubectl config set-context --current --namespace=datasource-clj
    kubectl apply -k ./k3s

Then point your browser to [URL](http://grafana.localhost). First time
login  will   be  admin:admin.    Data  source   and  a   simple  demo
[dashboard](./k3s/simple-dashboard.json) should  have been provisioned
by "kubectl  apply" as well.  This  Grafana instance makes use  of the
data source at http://datasource made available at this name & port by
the Kubernetes Service.  The Clojure  process inside the pod should be
addressed at  http://localhost:8080 ---  that is the  "localhost" from
the inside  of the pod, not  your laptop hosting k3s,  of course.  See
[datasources](./k3s/datasources.yaml).   The  Docker  Image  for  data
source     backend    ist     taken    from     Docker    Hub,     see
[Deployment](./k3s/deployment.yaml).

## Running Data Source interactively

During development  where you want  to quickly try a  locally modified
Data Source backend you may consider running

    $ lein run

or even  start it in  the CIDER REPL. The  data source will  listen at
0.0.0.0:8080,  outside  of  Kubernetes.   Then  point  the  Kubernetes
Service  there  by  replacing  the   Pod  selector  with  an  explicit
"external"      IP      in      the     Edpoints      object,      see
[Deployment](./k3s/deployment.yaml).   To see  the  Endpoints you  are
currently using issue

    $ kubectl get ep
    NAME         ENDPOINTS          AGE
    grafana      10.42.0.15:3000    60d
    datasource   192.168.0.1:8080   60d

In  this   particular  example   the  Endpoint   192.168.0.1:8080  was
configured outside of k3s.

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

    mkdir -p dir && curl -s "http://grafana.localhost/api/datasources" -u admin:password | jq -c -M '.[]' | split -l 1 - dir/

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
