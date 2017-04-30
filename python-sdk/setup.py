try:
    from setuptools import setup
except ImportError:
    from distutils.core import setup

__author__ = "The ActionML Team"
__email__ = "support@actionml.com"
__copyright__ = "Copyright 2017, ActionMl, Inc."
__license__ = "Apache License, Version 2.0"

setup(
    name='ActionML',
    version="0.0.8",
    author=__author__,
    author_email=__email__,
    packages=['actionml'],
    url='http://actionml.com',
    license='LICENSE.txt',
    description='ActionML Python SDK',
    classifiers=[
        'Programming Language :: Python',
        'License :: OSI Approved :: Apache Software License',
        'Operating System :: OS Independent',
        'Development Status :: 4 - Beta',
        'Intended Audience :: Developers',
        'Intended Audience :: Science/Research',
        'Environment :: Web Environment',
        'Topic :: Internet :: WWW/HTTP',
        'Topic :: Scientific/Engineering :: Artificial Intelligence',
        'Topic :: Scientific/Engineering :: Information Analysis',
        'Topic :: Software Development :: Libraries :: Python Modules'],
    long_description="""ActionML Python SDK
                       ActionML is a prediction server for building smart
                       applications. While you search data through a database
                       server, you can make prediction through ActionML.
                       With ActionML, you can write apps
                       - that predict user behaviors based on solid data
                         science
                       - using your choice of state-of-the-art machine
                         learning algorithms
                       - without worrying about scalability
                       Detailed documentation is available on our
                       `documentation site <a href="http://actionml.com/docs">docs</a>`.
                       This module provides convenient access of the
                       ActionML API to Python programmers so that they
                       can focus on their application logic.
                       """,
    install_requires=["pytz >= 2017.2", ],
)