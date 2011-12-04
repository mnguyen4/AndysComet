package Sprocket::Server::UNIX;

use strict;
use warnings;

use POE;
use Sprocket qw( Server );
use Sprocket::Common qw( super_event );
use Socket qw( AF_UNIX PF_UNIX );
use base qw( Sprocket::Server );

sub spawn {
    my $class = shift;
   
    my $self = $class->SUPER::spawn(
        @_,
        UNIX => 1,
    );

    return $self;
}

sub check_params {
    my $self = shift;
    
    die "You must specify a UNIX ListenAddress"
        unless ( $self->{opts}->{listen_address} );
    
    $self->{name} ||= 'UNIX Server';
    $self->{opts}->{domain} ||= AF_UNIX;
    $self->{opts}->{listen_queue} ||= 10000;
    $self->{opts}->{reuse} ||= 'yes';

    return;
}

sub server_start {
    my $self = $_[ OBJECT ];
    
    # create a socket factory
    $self->{wheel} = POE::Wheel::SocketFactory->new(
        BindAddress    => $self->{opts}->{listen_address},
        SocketDomain   => $self->{opts}->{domain},
        Reuse          => $self->{opts}->{reuse},
        SuccessEvent   => 'local_accept',
        FailureEvent   => 'local_wheel_error',
        ListenQueue    => $self->{opts}->{listen_queue},
    );

    $self->listen_address( $self->{opts}->{listen_address} );

    $self->_log(v => 2, msg => sprintf( "Listening on UNIX socket %s", $self->listen_address ) );
}

sub local_accept {
    my ( $kernel, $self, $socket, $peer_ip, $peer_port ) =
        @_[ KERNEL, OBJECT, ARG0, ARG1, ARG2 ];

    my $con = $self->new_connection(
        local_ip => 'LOCAL',
        local_port => 0,
        peer_ip => 'LOCAL',
        peer_hostname => 'LOCAL',
        peer_port => 0,
        peer_addr => "LOCAL:0",
        socket => $socket,
    );

    $self->process_plugins( [ 'local_accept', $self, $con, $socket ] );
    
    return;

}

1;

__END__

=head1 NAME

Sprocket::Server::UNIX - Sprocket Server for UNIX Sockets

=head1 SYNOPSIS

    use Sprocket qw( Server::UNIX );
    use Socket qw( PF_UNIX AF_UNIX );
    
    Sprocket::Server::UNIX->spawn(
        Name => 'Test Server',
        ListenAddress => '/tmp/myunixsocket',
        Domain => PF_UNIX, # Defaults to AF_UNIX
        Plugins => [
            {
                plugin => MyPlugin->new(),
                priority => 0, # default
            },
        ],
        LogLevel => 4,
        MaxConnections => 10000,
        Processes => 4,
    );


=head1 DESCRIPTION

Sprocket::Server::PreFork forks processes for Sprocket::Server

=head1 NOTE

This module subclasses L<Sprocket:Server> with one additional parameter:
Processes => (Int).  It will fork 3 additional processes to total 4.

=head1 SEE ALSO

L<POE>, L<Sprocket>, L<Sprocket::Connection>, L<Sprocket::Plugin>,
L<Sprocket::Client>, L<Sprocket::Server>

=head1 AUTHOR

David Davis E<lt>xantus@cpan.orgE<gt>

=head1 RATING

Please rate this module.
L<http://cpanratings.perl.org/rate/?distribution=Sprocket>

=head1 COPYRIGHT AND LICENSE

Copyright 2006-2007 by David Davis

See L<Sprocket> for license information.

=cut

