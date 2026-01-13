from dd_rca.models import Report, Symptom


def test_report_to_dict_has_expected_keys():
    r = Report(
        meta={"seed_type": "logs"},
        windows={"incident": {}, "baseline": {}},
        scope={},
        symptoms=[Symptom(type="log_signature", query_or_signature="q")],
        findings={},
        recommendations=[],
    )
    d = r.to_dict()
    assert set(d.keys()) == {"meta", "windows", "scope", "symptoms", "findings", "recommendations"}

