package com.rental.Marketplace;

import com.marketplace.payment.FakeRazorpayGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FakeRazorpayProfileGuardTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(FakeRazorpayGateway.class);

    @Test
    void fakeGatewayFailsStartupUnderProdProfile() {
        runner.withPropertyValues("payment.razorpay.fake=true")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("only allowed with the dev or test profile"));
    }

    @Test
    void fakeGatewayFailsStartupEvenWhenProdIsCombinedWithDev() {
        runner.withPropertyValues("payment.razorpay.fake=true")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev", "prod"))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void fakeGatewayFailsStartupUnderAnyOtherProfile() {
        runner.withPropertyValues("payment.razorpay.fake=true")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("staging"))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void fakeGatewayStartsUnderDevProfileAndIsAbsentWhenDisabled() {
        runner.withPropertyValues("payment.razorpay.fake=true")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .run(context -> assertThat(context).hasSingleBean(FakeRazorpayGateway.class));
        runner.withPropertyValues("payment.razorpay.fake=false")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .run(context -> assertThat(context).doesNotHaveBean(FakeRazorpayGateway.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void prodConfigurationKeepsTheFakeDisabled() throws Exception {
        try (InputStream yaml = getClass().getResourceAsStream("/application-prod.yml")) {
            Map<String, Object> config = new Yaml().load(yaml);
            Map<String, Object> razorpay = (Map<String, Object>) ((Map<String, Object>) config.get("payment")).get("razorpay");
            assertThat(razorpay.get("fake")).isEqualTo(false);
        }
    }
}
