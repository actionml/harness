package com.actionml.router.config

trait ConfigurationComponent {
  def config: AppConfig
}

class ConfigurationComponentImpl extends ConfigurationComponent {
  override def config = AppConfig.apply
}
