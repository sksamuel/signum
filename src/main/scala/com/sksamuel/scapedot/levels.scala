package com.sksamuel.scapedot

/**
 * Indicates the severity level of an inspection.
 *
 * Errors indicate code that is potentially unsafe or likely to lead to bugs.
 *
 * Warnings are reserved for code that has bad semantics.
 * This by itself does not necessarily mean the code is buggy, but could mean the developer
 * made a mistake or does not fully understand the contructs or best practice.
 *
 * An example is an expression as a statement. While this is perfectly legal, it could indicate
 * that the developer meant to assign the result to or otherwise use it.
 *
 * Another example is a constant if. You can do things like if (true) { } if you want, but since the block
 * will always evaluate, the if statement perhaps indicates a mistake.
 *
 * Infos are used for code which is semantically fine, but there exists a more idomatic way of writing it.
 *
 * An example would be using an if statement to return true or false as the last statement in a block.
 * Eg,
 *
 * def foo = {
 *   if (a) true else false
 * }
 *
 * Can be re-written as
 *
 * def foo = a
 */
enum Level:
  case Error, Warning, Info