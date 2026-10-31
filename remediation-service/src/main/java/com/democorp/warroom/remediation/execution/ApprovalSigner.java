package com.democorp.warroom.remediation.execution;

import com.democorp.warroom.remediation.RemediationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

/** HMAC-SHA256 over exactly what was approved. Change the action, target or approvers and the signature breaks. */
@Component
public class ApprovalSigner {

    private final byte[] key;

    public ApprovalSigner(RemediationProperties props) {
        this.key = props.signingKey().getBytes(StandardCharsets.UTF_8);
    }

    public String sign(String actionId, String action, String target, List<String> approvals) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            String payload = actionId + "|" + action + "|" + target + "|" + String.join(",", approvals);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC not available", e);
        }
    }

    public boolean verify(String signature, String actionId, String action, String target, List<String> approvals) {
        if (signature == null) {
            return false;
        }
        return MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8),
                sign(actionId, action, target, approvals).getBytes(StandardCharsets.UTF_8));
    }
}
