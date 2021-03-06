package com.galacticfog

import java.util.UUID

package object gestalt {

  object ResourceIds {
    val ActionProvider = UUID.fromString("bb3dc8bb-7a9b-43aa-b9db-aaa44fb5d099")
    val Api = UUID.fromString("df4bf8b5-170f-453e-9526-c37c898d96c9")
    val ApiEndpoint = UUID.fromString("f3f1ad01-73f1-48a8-9d24-e41d8a202545")
    val ApiGatewayProvider = UUID.fromString("956a8a20-57f7-456e-aa9e-b2cba5dfc341")
    val BashExecutor       = UUID.fromString("8c074a8d-ad5b-4ef7-a77c-3dc4d5e47c89")
    val Blueprint = UUID.fromString("b61a80a8-b348-4435-bf4d-760d0badf58d")
    val BlueprintProvider  = UUID.fromString("510cb184-d33e-4346-a58b-755980539cc7")
    val CaasProvider       = UUID.fromString("79075694-6510-49d3-8bd5-a8dce581bd48")
    val Configuration = UUID.fromString("1896a921-afaa-437d-b6d8-70787d46b32b")
    val Container = UUID.fromString("28cbf0e0-2c48-4589-85d5-df97a3a1f318")
    val CsharpExecutor     = UUID.fromString("421d0798-1e93-433c-a103-3b48fe578dbf")
    val DataContainer = UUID.fromString("45bc59bf-e6b7-4537-b685-a7aa32f56ddd")
    val DataProvider       = UUID.fromString("d15ac621-189f-4463-ab46-caf8f61d9b52")
    val DcosProvider       = UUID.fromString("26a2694e-ddc7-4a34-a910-f794cc2417e4")
    val DockerProvider     = UUID.fromString("a3f6c87f-dbf6-4863-8921-f25e991ff2d2")
    val Domain = UUID.fromString("89f19a93-b7ef-4681-b768-018933ca6b30")
    val Endpoint = UUID.fromString("9a4e33de-8d9d-4aaa-a994-290062afb681")
    val Entitlement = UUID.fromString("4332a8c1-d94e-4a68-b1aa-55fd95a959c3")
    val Environment = UUID.fromString("46b28076-9d94-4860-9aca-e8483a56d32c")
    val EventsProvider     = UUID.fromString("f6e8970d-9d41-4b68-9d51-3864d065b0ce")
    val ExecutorProvider   = UUID.fromString("2b74ef5e-5c6a-4e96-834c-15cb6e2bf3a8")
    val GatewayManager     = UUID.fromString("a695c8ca-b429-4127-80bb-688583880257")
    val GoLangExecutor     = UUID.fromString("019ea97a-d2c5-4891-b197-c23b3a7d3112")
    val Group = UUID.fromString("ad4c7258-1896-45bf-8ec8-c875dcc7654e")
    val Integration = UUID.fromString("1cc1794a-9a13-11e6-9f33-a24fc0d9649c")
    val JavaExecutor       = UUID.fromString("d6197e9c-7096-48c3-b645-b0e3cd52adcf")
    val KongGateway        = UUID.fromString("25acb32c-6635-49d1-ba19-4cf317003ff6")
    val KubeProvider       = UUID.fromString("9c59ed05-4e2f-4875-97ba-7e1fb175ddd9")
    val Lambda = UUID.fromString("e3a463fc-e51f-4166-8cec-3d9a54f7babd")
    val LambdaProvider     = UUID.fromString("e04778bd-2504-4635-85a9-1b2b293a2d41")
    val LdapProvider       = UUID.fromString("9a7a7169-391f-4ba5-9b84-3ba9af0d2ab2")
    val License = UUID.fromString("b2ee3bea-b5ee-4c7a-98e3-897827c1d88f")
    val LoggingProvider    = UUID.fromString("e1782fef-4b7c-4f75-b8b8-6e6e2ecd82b2")
    val MessageEndpoint = UUID.fromString("3a9bb2e4-d5e2-463b-a59f-847ff6665ec6")
    val MessageProvider    = UUID.fromString("80d48917-7e05-47c6-a5b6-613a2d55b58a")
    val NashornExecutor    = UUID.fromString("240c8258-2817-41db-9904-efe8573303fe")
    val NodeJsExecutor     = UUID.fromString("06584400-e3ba-449f-bb16-7113e1ff5d84")
    val Org = UUID.fromString("23ba3299-b04e-4c5c-9b3d-64939b22944e")
    val Policy = UUID.fromString("c3c05a39-acb3-4a45-97a8-5696ad1b4214")
    val PolicyProvider     = UUID.fromString("9b6f7ec6-2e75-4b95-90dc-0e17935f8d4b")
    val PostgresProvider   = UUID.fromString("5cdb00e9-2861-4a02-a4ab-c926d7200490")
    val Provider = UUID.fromString("40606f36-6f55-49bf-9814-fd9702b1e23d")
    val ProviderAction = UUID.fromString("b2eb41f7-3b93-45cb-9435-7d44968d6105")
    val PythonExecutor     = UUID.fromString("950db9eb-ea5a-4c65-a445-8d0a85af1bdd")
    val RabbitProvider     = UUID.fromString("39cb96f1-0dc8-4ce6-9d88-863988fa1e16")
    val Resource = UUID.fromString("18dcdc3d-3d1a-40de-8810-c3493c212ef0")
    val ResourceContainer = UUID.fromString("f3f8fa3a-40ce-477c-8589-927072569b94")
    val RubyExecutor       = UUID.fromString("790e9ae3-093a-426d-a570-bb7739c98850")
    val Rule = UUID.fromString("51af837d-544d-4c60-be66-984a6f2f658a")
    val RuleConfig = UUID.fromString("be965617-bd78-4491-9506-35f1814e6207")
    val RuleEvent = UUID.fromString("55fc08d0-2593-43a1-9a65-cbdb00e5ac52")
    val RuleLimit = UUID.fromString("1e1bbfd6-e368-42ef-ba3a-6d8fe7519659")
    val Runnable = UUID.fromString("284a7f2b-8cb8-4a17-8c6f-001b018afbbb")
    val ScalaExecutor      = UUID.fromString("8e9263b4-55e4-4762-beac-81393458a274")
    val Secret = UUID.fromString("98d8a6d6-e3db-488f-b32d-8b00b3fce472")
    val SecurityProvider   = UUID.fromString("152cbb67-444e-4787-9c13-d3a9cc385e3e")
    val SystemConfig  = UUID.fromString("c7fefa43-c1f4-4f75-a7fd-ea05cfe9663d")
    val TypeProperty = UUID.fromString("36f4289c-b0e0-492a-9d4f-83d0cebf055c")
    val UiDescriptor = UUID.fromString("688d0b67-3783-4eef-9954-bb200b626a1c")
    val User = UUID.fromString("58b6775d-37a5-44bc-9115-7331fc4964b7")
    val Workspace = UUID.fromString("fa17bae4-1294-42cc-93cc-c4ead7dc0343")

    /** These are Reference Types */
    val DataType = UUID.fromString("8f8b6e85-6e9c-49c5-b160-475cdffeab8f")
    val EnvironmentType = UUID.fromString("b39af336-11d6-4f52-9c34-739615cefbd1")
    val NodeType = UUID.fromString("7d6ebd0c-60a7-4147-8032-11a3a845ab09")
    val RequirementType = UUID.fromString("e93ca998-c05e-41f8-9c61-4e9550426a7d")
    val ResourceState = UUID.fromString("9049b2fa-bef6-41d1-9cb2-6edb02ec9961")
    val ResourceType = UUID.fromString("349f8c02-ff9c-4cdb-badf-4c52be6e9d5b")
    val TaskStatusType = UUID.fromString("6edd1c7c-37b9-4e4f-aa22-10d3e2026d23")
    val TypeAction = UUID.fromString("bc83a824-173a-40fd-a0cf-f8ce94d1d3fe")
    val VisibilityType = UUID.fromString("4f952c9a-0daf-4671-b298-56768b1c795f")
  }

  val executorProviders: Set[UUID] = Set(
    ResourceIds.GoLangExecutor,
    ResourceIds.JavaExecutor,
    ResourceIds.NashornExecutor,
    ResourceIds.NodeJsExecutor,
    ResourceIds.PythonExecutor,
    ResourceIds.RubyExecutor,
    ResourceIds.CsharpExecutor
  )

  object Resources {
    val Resource          = "Gestalt::Resource"
    val Api               = "Gestalt::Resource::Api"
    val ApiEndpoint       = "Gestalt::Resource::ApiEndpoint"
    val Blueprint         = "Gestalt::Resource::Blueprint"
    val Container         = "Gestalt::Resource::Container"
    val DataContainer     = "Gestalt::Resource::DataContainer"
    val Domain            = "Gestalt::Resource::Domain"
    val Endpoint          = "Gestalt::Resource::Endpoint"
    val MessageEndpoint   = "Gestalt::Resource::Endpoint::MessageEndpoint"
    val Entitlement       = "Gestalt::Resource::Entitlement"
    val Environment       = "Gestalt::Resource::Environment"
    val Group             = "Gestalt::Resource::Group"
    val Integration       = "Gestalt::Resource::Integration"
    val License           = "Gestalt::Resource::License"
    val Lambda            = "Gestalt::Resource::Node::Lambda"
    val Org               = "Gestalt::Resource::Organization"
    val Policy            = "Gestalt::Resource::Policy"
    val ProviderAction    = "Gestalt::Resource::ProviderAction"
    val ResourceContainer = "Gestalt::Resource::ResourceContainer"
    val Rule              = "Gestalt::Resource::Rule"
    val RuleConfig        = "Gestalt::Resource::Rule::Config"
    val RuleEvent         = "Gestalt::Resource::Rule::Event"
    val RuleLimit         = "Gestalt::Resource::Rule::Limit"
    val Runnable          = "Gestalt::Resource::Runnable"
    val Secret            = "Gestalt::Resource::Secret"
    val EnvironmentType   = "Gestalt::Resource::Type::Environment"
    val UiDescriptor      = "Gestalt::Resource::UiDescriptor"
    val User              = "Gestalt::Resource::User"
    val Workspace         = "Gestalt::Resource::Workspace"

    val Configuration     = "Gestalt::Configuration"
    val Provider          = "Gestalt::Configuration::Provider"
    val ActionProvider    = "Gestalt::Configuration::Provider::ActionProvider"
    val BlueprintProvider = "Gestalt::Configuration::Provider::Blueprint"
    val CaasProvider      = "Gestalt::Configuration::Provider::CaaS"
    val DcosProvider      = "Gestalt::Configuration::Provider::CaaS::DCOS"
    val DockerProvider    = "Gestalt::Configuration::Provider::CaaS::Docker"
    val KubeProvider      = "Gestalt::Configuration::Provider::CaaS::Kubernetes"
    val DataProvider      = "Gestalt::Configuration::Provider::Data"
    val PostgresProvider  = "Gestalt::Configuration::Provider::Data::PostgreSQL"
    val EventsProvider    = "Gestalt::Configuration::Provider::Events"
    val GatewayManager    = "Gestalt::Configuration::Provider::GatewayManager"
    val KongGateway       = "Gestalt::Configuration::Provider::Kong"
    val LambdaProvider    = "Gestalt::Configuration::Provider::Lambda"
    val ExecutorProvider  = "Gestalt::Configuration::Provider::Lambda::Executor"
    val BashExecutor      = "Gestalt::Configuration::Provider::Lambda::Executor::Bash"
    val CsharpExecutor    = "Gestalt::Configuration::Provider::Lambda::Executor::CSharp"
    val GoLangExecutor    = "Gestalt::Configuration::Provider::Lambda::Executor::GoLang"
    val JavaExecutor      = "Gestalt::Configuration::Provider::Lambda::Executor::Java"
    val NashornExecutor   = "Gestalt::Configuration::Provider::Lambda::Executor::Nashorn"
    val NodeJsExecutor    = "Gestalt::Configuration::Provider::Lambda::Executor::NodeJS"
    val PythonExecutor    = "Gestalt::Configuration::Provider::Lambda::Executor::Python"
    val RubyExecutor      = "Gestalt::Configuration::Provider::Lambda::Executor::Ruby"
    val ScalaExecutor     = "Gestalt::Configuration::Provider::Lambda::Executor::Scala"
    val LdapProvider      = "Gestalt::Configuration::Provider::Ldap"
    val Logging           = "Gestalt::Configuration::Provider::Logging"
    val MessageProvider   = "Gestalt::Configuration::Provider::Messaging"
    val RabbitProvider    = "Gestalt::Configuration::Provider::Messaging::RabbitMQ"
    val PolicyProvider    = "Gestalt::Configuration::Provider::Policy"
    val SecurityProvider  = "Gestalt::Configuration::Provider::Security"
  }

  def resourceId(tpe: String): UUID = tpe match {
    case Resources.ActionProvider     => ResourceIds.ActionProvider
    case Resources.Api                => ResourceIds.Api
    case Resources.ApiEndpoint        => ResourceIds.ApiEndpoint
    case Resources.BashExecutor       => ResourceIds.BashExecutor
    case Resources.Blueprint          => ResourceIds.Blueprint
    case Resources.BlueprintProvider  => ResourceIds.BlueprintProvider
    case Resources.CaasProvider       => ResourceIds.CaasProvider
    case Resources.Configuration      => ResourceIds.Configuration
    case Resources.Container          => ResourceIds.Container
    case Resources.CsharpExecutor     => ResourceIds.CsharpExecutor
    case Resources.DataContainer      => ResourceIds.DataContainer
    case Resources.DataProvider       => ResourceIds.DataProvider
    case Resources.DcosProvider       => ResourceIds.DcosProvider
    case Resources.DockerProvider     => ResourceIds.DockerProvider
    case Resources.Domain             => ResourceIds.Domain
    case Resources.Endpoint           => ResourceIds.Endpoint
    case Resources.Entitlement        => ResourceIds.Entitlement
    case Resources.Environment        => ResourceIds.Environment
    case Resources.EventsProvider     => ResourceIds.EventsProvider
    case Resources.ExecutorProvider   => ResourceIds.ExecutorProvider
    case Resources.GatewayManager     => ResourceIds.GatewayManager
    case Resources.GoLangExecutor     => ResourceIds.GoLangExecutor
    case Resources.Group              => ResourceIds.Group
    case Resources.Integration        => ResourceIds.Integration
    case Resources.JavaExecutor       => ResourceIds.JavaExecutor
    case Resources.KongGateway        => ResourceIds.KongGateway
    case Resources.KubeProvider       => ResourceIds.KubeProvider
    case Resources.Lambda             => ResourceIds.Lambda
    case Resources.LambdaProvider     => ResourceIds.LambdaProvider
    case Resources.LdapProvider       => ResourceIds.LdapProvider
    case Resources.License            => ResourceIds.License
    case Resources.Logging            => ResourceIds.LoggingProvider
    case Resources.MessageEndpoint    => ResourceIds.MessageEndpoint
    case Resources.MessageProvider    => ResourceIds.MessageProvider
    case Resources.NashornExecutor    => ResourceIds.NashornExecutor
    case Resources.NodeJsExecutor     => ResourceIds.NodeJsExecutor
    case Resources.Org                => ResourceIds.Org
    case Resources.Policy             => ResourceIds.Policy
    case Resources.PolicyProvider     => ResourceIds.PolicyProvider
    case Resources.PostgresProvider   => ResourceIds.PostgresProvider
    case Resources.Provider           => ResourceIds.Provider
    case Resources.ProviderAction     => ResourceIds.ProviderAction
    case Resources.PythonExecutor     => ResourceIds.PythonExecutor
    case Resources.RabbitProvider     => ResourceIds.RabbitProvider
    case Resources.Resource           => ResourceIds.Resource
    case Resources.ResourceContainer  => ResourceIds.ResourceContainer
    case Resources.RubyExecutor       => ResourceIds.RubyExecutor
    case Resources.Rule               => ResourceIds.Rule
    case Resources.RuleConfig         => ResourceIds.RuleConfig
    case Resources.RuleEvent          => ResourceIds.RuleEvent
    case Resources.RuleLimit          => ResourceIds.RuleLimit
    case Resources.Runnable           => ResourceIds.Runnable
    case Resources.ScalaExecutor      => ResourceIds.ScalaExecutor
    case Resources.Secret             => ResourceIds.Secret
    case Resources.SecurityProvider   => ResourceIds.SecurityProvider
    case Resources.UiDescriptor       => ResourceIds.UiDescriptor
    case Resources.User               => ResourceIds.User
    case Resources.Workspace          => ResourceIds.Workspace
    case s => throw new RuntimeException(s"could not find resource_type with label '$s'")
  }

  def resourceName(typeId: UUID) = {
    typeId match {
      case ResourceIds.ActionProvider     => Resources.ActionProvider
      case ResourceIds.Api                => Resources.Api
      case ResourceIds.ApiEndpoint        => Resources.ApiEndpoint
      case ResourceIds.BashExecutor       => Resources.BashExecutor
      case ResourceIds.Blueprint          => Resources.Blueprint
      case ResourceIds.BlueprintProvider  => Resources.BlueprintProvider
      case ResourceIds.CaasProvider       => Resources.CaasProvider
      case ResourceIds.Configuration      => Resources.Configuration
      case ResourceIds.Container          => Resources.Container
      case ResourceIds.CsharpExecutor     => Resources.CsharpExecutor
      case ResourceIds.DataContainer      => Resources.DataContainer
      case ResourceIds.DataProvider       => Resources.DataProvider
      case ResourceIds.DcosProvider       => Resources.DcosProvider
      case ResourceIds.DockerProvider     => Resources.DockerProvider
      case ResourceIds.Domain             => Resources.Domain
      case ResourceIds.Endpoint           => Resources.Endpoint
      case ResourceIds.Entitlement        => Resources.Entitlement
      case ResourceIds.Environment        => Resources.Environment
      case ResourceIds.EnvironmentType    => Resources.EnvironmentType
      case ResourceIds.EventsProvider     => Resources.EventsProvider
      case ResourceIds.ExecutorProvider   => Resources.ExecutorProvider
      case ResourceIds.GatewayManager     => Resources.GatewayManager
      case ResourceIds.GoLangExecutor     => Resources.GoLangExecutor
      case ResourceIds.Group              => Resources.Group
      case ResourceIds.Integration        => Resources.Integration
      case ResourceIds.JavaExecutor       => Resources.JavaExecutor
      case ResourceIds.KongGateway        => Resources.KongGateway
      case ResourceIds.KubeProvider       => Resources.KubeProvider
      case ResourceIds.Lambda             => Resources.Lambda
      case ResourceIds.LambdaProvider     => Resources.LambdaProvider
      case ResourceIds.License            => Resources.License
      case ResourceIds.LoggingProvider    => Resources.Logging
      case ResourceIds.MessageEndpoint    => Resources.MessageEndpoint
      case ResourceIds.MessageProvider    => Resources.MessageProvider
      case ResourceIds.NashornExecutor    => Resources.NashornExecutor
      case ResourceIds.NodeJsExecutor     => Resources.NodeJsExecutor
      case ResourceIds.Org                => Resources.Org
      case ResourceIds.Policy             => Resources.Policy
      case ResourceIds.PolicyProvider     => Resources.PolicyProvider
      case ResourceIds.PostgresProvider   => Resources.PostgresProvider
      case ResourceIds.Provider           => Resources.Provider
      case ResourceIds.ProviderAction     => Resources.ProviderAction
      case ResourceIds.PythonExecutor     => Resources.PythonExecutor
      case ResourceIds.RabbitProvider     => Resources.RabbitProvider
      case ResourceIds.Resource           => Resources.Resource
      case ResourceIds.ResourceContainer  => Resources.ResourceContainer
      case ResourceIds.RubyExecutor       => Resources.RubyExecutor
      case ResourceIds.Rule               => Resources.Rule
      case ResourceIds.RuleConfig         => Resources.RuleConfig
      case ResourceIds.RuleEvent          => Resources.RuleEvent
      case ResourceIds.RuleLimit          => Resources.RuleLimit
      case ResourceIds.Runnable           => Resources.Runnable
      case ResourceIds.ScalaExecutor      => Resources.ScalaExecutor
      case ResourceIds.Secret             => Resources.Secret
      case ResourceIds.SecurityProvider   => Resources.SecurityProvider
      case ResourceIds.UiDescriptor       => Resources.UiDescriptor
      case ResourceIds.User               => Resources.User
      case ResourceIds.Workspace          => Resources.Workspace
      case _ => s"resourceType[${typeId}]"
    }
  }

}
