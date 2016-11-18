package com.mesosphere.cosmos.rpc.v1.model

import com.mesosphere.universe

// This class is only a wrapper for the underlying package
// We need a unique type so that the media type encoder can be resolved implicitly
// TODO package-add: Change argument to a V3Package?
final class AddResponse(val packageDefinition: universe.v3.model.PackageDefinition) extends AnyVal
