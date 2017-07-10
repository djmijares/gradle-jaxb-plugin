package org.openrepose.gradle.plugins.jaxb.task

import org.openrepose.gradle.plugins.jaxb.ant.AntXjc
import org.openrepose.gradle.plugins.jaxb.tree.TreeManager
import org.openrepose.gradle.plugins.jaxb.JaxbPlugin
import org.openrepose.gradle.plugins.jaxb.ProjectTaskSpecification
import org.openrepose.gradle.plugins.jaxb.converter.NamespaceToEpisodeConverter
import org.openrepose.gradle.plugins.jaxb.resolver.EpisodeDependencyResolver
import org.openrepose.gradle.plugins.jaxb.resolver.XjcResolver

class JaxbXjcSpec extends ProjectTaskSpecification {

  def treeManager = Mock(TreeManager)
  def episodeResolver = Mock(EpisodeDependencyResolver)
  def resolver = Mock(XjcResolver)
  def converter = Mock(NamespaceToEpisodeConverter)
  def executor = Mock(AntXjc)

  def setup() {
    task = project.tasks[JaxbPlugin.JAXB_XJC_TASK] as JaxbXjc
    task.with {
      bindings = project.files()
      manager = treeManager
      dependencyResolver = episodeResolver
      xjcResolver = resolver
      episodeConverter = converter
      xjc = executor
    }
    // set up tree manager as it would look like at this point in execution
    def managedNodes = nodes as Set
    def treeRoot = nodes.findAll {
      ["xsd1", "xsd2"].contains(it.data.namespace)
    } as LinkedList
    def middleRow = nodes.findAll {
      ["xsd3", "xsd4", "xsd5"].contains(it.data.namespace)
    } as Set
    def lastRow = nodes.findAll {
      ["xsd6", "xsd7", "xsd8", "xsd9"].contains(it.data.namespace)
    } as Set

    // mock manager methods
    with(treeManager) {
      getManagedNodes() >> managedNodes
      getTreeRoot() >> treeRoot
      getNextDescendants(treeRoot) >> middleRow
      getNextDescendants(middleRow) >> lastRow
      getNextDescendants(lastRow) >> null
    }
  }

  def "run through task with given tree manager"() {
    given: "for a namespace, set up returns from mocks in task"
    def serviceResults = ["xsd1": [],
                          "xsd2": [],
                          "xsd3": ["xsd1.episode"],
                          "xsd4": ["xsd1.episode", "xsd2.episode"],
                          "xsd5": ["xsd2.episode"],
                          "xsd6": ["xsd1.episode", "xsd3.episode"],
                          "xsd7": ["xsd1.episode", "xsd3.episode"],
                          "xsd8": ["xsd4.episode", "xsd5.episode", "xsd1.episode", "xsd2.episode"],
                          "xsd9": ["xsd5.episode", "xsd2.episode"]]

    when:
    task.start()

    then:
    serviceResults.each { ns, dependencies ->
      def node = nodes.find { it.data.namespace == ns }
      def jaxbConfig = project.configurations[JaxbPlugin.JAXB_CONFIGURATION_NAME].asPath
      def episodeFile = project.file(new File(project.jaxb.episodesDir, ns + ".episode"))
      def episodes = dependencies.collect { new File(project.jaxb.episodesDir, it) } as Set
      def xsds = [new File(ns + ".xsd")] as Set

      1 * episodeResolver.resolve(node, converter, _ as File) >> episodes
      1 * resolver.resolve(node.data) >> xsds
      1 * converter.convert(node.data.namespace) >> ns + ".episode"
      1 * executor.execute(_ as AntBuilder,
               project.jaxb.xjc,
               jaxbConfig,
               jaxbConfig,
			   { it.files == project.files(xsds).files },
			   task.bindings,
			   { it.files == project.files(episodes).files },
			   episodeFile)
    }
  }

  def "run through task with given tree manager and don't generate episode files"() {
    given: "for a namespace, set up returns from mocks in task"
    project.jaxb.xjc.generateEpisodeFiles = false
    def serviceResults = ["xsd1": [],
                          "xsd2": [],
                          "xsd3": ["xsd1.episode"],
                          "xsd4": ["xsd1.episode", "xsd2.episode"],
                          "xsd5": ["xsd2.episode"],
                          "xsd6": ["xsd1.episode", "xsd3.episode"],
                          "xsd7": ["xsd1.episode", "xsd3.episode"],
                          "xsd8": ["xsd4.episode", "xsd5.episode", "xsd1.episode", "xsd2.episode"],
                          "xsd9": ["xsd5.episode", "xsd2.episode"]]

    when:
    task.start()

    then:
    serviceResults.each { ns, dependencies ->
      def node = nodes.find { it.data.namespace == ns }
      def jaxbConfig = project.configurations[JaxbPlugin.JAXB_CONFIGURATION_NAME].asPath
      def episodeFile = null
      def episodes = [] as Set
      def xsds = [new File(ns + ".xsd")] as Set
      1 * episodeResolver.resolve(node, converter, _ as File) >> episodes
      1 * resolver.resolve(node.data) >> xsds
      0 * converter.convert(node.data.namespace) >> ns + ".episode"
      1 * executor.execute(_ as AntBuilder,
              project.jaxb.xjc,
              jaxbConfig,
              jaxbConfig,
              { it.files == project.files(xsds).files },
              task.bindings,
              { it.files == project.files(episodes).files },
              episodeFile)
    }
  }
}
