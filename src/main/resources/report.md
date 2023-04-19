# SHACL Validation Report

|   |   |
|---|---|
| Date | {{ timestamp }} |
| SHACL file | {{ shacl }} |
| Data file | {{ data }} |

---

## Errors: : {{ errors|length }}
{% for error in errors | sort %}

### {{ error.message }} : {{ error.issues|length }} errors

```
{{ error.shape }}
```

| Focus node | Value |
|------------|-------|
{% for issue in error.issues %}
| {{ issue.node }} | {{ issue.value }} |
{% endfor %}

{% endfor %}

---

## Warnings: {{ warnings|length }}
{% for warning in warnings %}

### {{ warning.message }}

```
{{ warning.shape }}
```

| Focus node | Value |
|------------|-------|
{% for issue in warning.issues %}
| {{ issue.node }} | {{ issue.value }} |
{% endfor %}

{% endfor %}

---

## Recommendations: {{ infos|length }}
{% for info in infos %}

### {{ info.message }}

```
{{ info.shape }}
```

| Focus node | Value |
|------------|-------|
{% for issue in info.issues %}
| {{ issue.node }} | {{ issue.value }} |
{% endfor %}

{% endfor %}

---

## Statistics

{% if classes is not empty %}
### Classes

| Name | Count |
|------|-------|
{% for entry in classes|sort %}
| {{ entry.name }} | {{ entry.number }} |
{% endfor %}

{% endif %}

{% if properties is not empty %}
### Properties

| Name | Count |
|------|-------|
{% for entry in properties|sort %}
| {{ entry.name }} | {{ entry.number }} |
{% endfor %}

{% endif %}

{% if values is not empty %}
### Values
{% for v in values %}
#### {{ v.key }}

| Name | Count |
|------|-------|
{% for entry in v.value|sort %}
| {{ entry.name }} | {{ entry.number }} |
{% endfor %}

{% endfor %}
{% endif %}
