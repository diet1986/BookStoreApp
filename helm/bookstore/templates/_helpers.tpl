{{/*
Expand the name of the chart.
*/}}
{{- define "bookstore.name" -}}
{{- .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create chart label
*/}}
{{- define "bookstore.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "bookstore.labels" -}}
helm.sh/chart: {{ include "bookstore.chart" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Build full image name
*/}}
{{- define "bookstore.image" -}}
{{- $registry := .Values.global.imageRegistry -}}
{{- $image := .image -}}
{{- $tag := .Values.global.imageTag -}}
{{- if $registry -}}
{{- printf "%s/%s:%s" $registry $image $tag -}}
{{- else -}}
{{- printf "%s:%s" $image $tag -}}
{{- end -}}
{{- end }}

{{/*
Common environment variables for all microservices
*/}}
{{- define "bookstore.commonEnv" -}}
- name: SPRING_PROFILES_ACTIVE
  value: {{ .Values.global.springProfile | quote }}
- name: CONSUL_HOST
  value: "consul-service"
- name: CONSUL_PORT
  value: "8500"
- name: ZIPKIN_HOST
  value: "zipkin-service"
- name: DB_HOST
  value: {{ .Values.database.host | quote }}
- name: DB_PORT
  value: {{ .Values.database.port | quote }}
- name: DB_NAME
  value: {{ .Values.database.name | quote }}
- name: DB_USER
  valueFrom:
    secretKeyRef:
      name: bookstore-secrets
      key: DB_USER
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: bookstore-secrets
      key: DB_PASSWORD
{{- end }}

{{/*
Readiness probe
*/}}
{{- define "bookstore.readinessProbe" -}}
readinessProbe:
  httpGet:
    path: /actuator/health
    port: {{ . }}
  initialDelaySeconds: 30
  periodSeconds: 10
  failureThreshold: 5
{{- end }}

{{/*
Liveness probe
*/}}
{{- define "bookstore.livenessProbe" -}}
livenessProbe:
  httpGet:
    path: /actuator/health
    port: {{ . }}
  initialDelaySeconds: 60
  periodSeconds: 30
  failureThreshold: 3
{{- end }}
