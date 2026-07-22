package com.agriinsight.backend.identity;

import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.identity.application.TenantUserCommandService;
import com.agriinsight.backend.identity.application.TenantUserService;
import com.agriinsight.backend.authorization.application.TenantRoleCommandService;
import com.agriinsight.backend.farm.application.FarmCommandService;
import com.agriinsight.backend.farm.application.FarmService;
import com.agriinsight.backend.farm.application.FarmAssignmentCommandService;
import com.agriinsight.backend.farm.application.FarmAssignmentService;
import com.agriinsight.backend.farm.application.CropCommandService;
import com.agriinsight.backend.farm.application.CropService;
import com.agriinsight.backend.farm.application.FieldCommandService;
import com.agriinsight.backend.farm.application.FieldService;
import com.agriinsight.backend.farm.application.SeasonCommandService;
import com.agriinsight.backend.farm.application.SeasonService;
import com.agriinsight.backend.operations.application.EmployeeCommandService;
import com.agriinsight.backend.operations.application.EmployeeService;
import com.agriinsight.backend.operations.application.ActivityCommandService;
import com.agriinsight.backend.operations.application.ActivityService;
import com.agriinsight.backend.operations.application.ActivityAssignmentCommandService;
import com.agriinsight.backend.operations.application.ActivityAssignmentService;
import com.agriinsight.backend.operations.application.ActivityLogCommandService;
import com.agriinsight.backend.operations.application.ActivityLogService;
import com.agriinsight.backend.operations.application.HarvestCommandService;
import com.agriinsight.backend.operations.application.HarvestService;
import com.agriinsight.backend.inventory.application.WarehouseCommandService;
import com.agriinsight.backend.inventory.application.WarehouseService;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(properties = {
        "agriinsight.identity.enabled=true",
        "agriinsight.identity.issuer-uri=https://identity.example.test/issuer",
        "agriinsight.identity.jwk-set-uri=https://identity.example.test/jwks",
        "agriinsight.identity.api-audience=agriinsight-api",
        "agriinsight.identity.interactive-client-id=interactive-client",
        "agriinsight.identity.clock-skew=30s",
        "agriinsight.identity.jws-algorithm=RS256",
        "agriinsight.identity.discriminator-location=CLAIM",
        "agriinsight.identity.discriminator-name=token_use",
        "agriinsight.identity.discriminator-value=access",
        "agriinsight.identity.display-name-claim=name",
        "agriinsight.identity.email-claim=email",
        "agriinsight.identity.assurance-claim=acr",
        "agriinsight.identity.cors-allowed-origins[0]=https://app.agriinsight.test",
        "agriinsight.api-docs.enabled=true",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@MockitoBean(types = {
        TenantPrincipalLoader.class,
        TenantUserService.class,
        TenantUserCommandService.class,
        TenantRoleCommandService.class,
        FarmService.class,
        FarmCommandService.class,
        FarmAssignmentService.class,
        FarmAssignmentCommandService.class,
        CropService.class,
        CropCommandService.class,
        FieldService.class,
        FieldCommandService.class,
        SeasonService.class,
        SeasonCommandService.class,
        EmployeeService.class,
        EmployeeCommandService.class,
        ActivityService.class,
        ActivityCommandService.class,
        ActivityAssignmentService.class,
        ActivityAssignmentCommandService.class,
        ActivityLogService.class,
        ActivityLogCommandService.class,
        HarvestService.class,
        HarvestCommandService.class,
        WarehouseService.class,
        WarehouseCommandService.class
})
public @interface IdentitySecurityContext {
}
