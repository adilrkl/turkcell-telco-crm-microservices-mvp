{{/*
Ortak chart etiketleri (her kaynakta tekrarlanmasin diye helper).
*/}}
{{- define "telco-crm.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service | quote }}
app.kubernetes.io/part-of: telco-crm
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}

{{/*
Servis girdisini defaults ile birlestirir. Kullanim:
  {{ $cfg := include "telco-crm.serviceConfig" (dict "root" $ "svc" $svc) | fromYaml }}
deepCopy sart: mergeOverwrite hedefi mutasyona ugratir, defaults'u bozmamali.
*/}}
{{- define "telco-crm.serviceConfig" -}}
{{- $merged := mustMergeOverwrite (deepCopy .root.Values.defaults) (deepCopy (default dict .svc)) -}}
{{- $merged | toYaml -}}
{{- end }}
